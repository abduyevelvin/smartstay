package com.smartstay.ai.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.smartstay.ai.client.HotelServiceClient;
import com.smartstay.ai.client.dto.CreateHotelClientRequest;
import com.smartstay.ai.client.dto.CreateRoomClientRequest;
import com.smartstay.ai.client.dto.HotelClientResponse;
import com.smartstay.ai.dto.AiHotelData;
import com.smartstay.ai.dto.AiRoomData;
import com.smartstay.ai.dto.HotelCreationPhase;
import com.smartstay.ai.dto.HotelCreationState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelCreationServiceImpl implements HotelCreationService {

    private final ChatClient chatClient;
    private final HotelServiceClient hotelServiceClient;

    /**
     * Active creation sessions keyed by {@code userId:sessionId}.
     */
    private final Map<String, HotelCreationState> sessions = new ConcurrentHashMap<>();

    private static final Set<String> VALID_ROOM_TYPES = Set.of("SINGLE", "DOUBLE", "SUITE", "PENTHOUSE");

    // ── Public API ────────────────────────────────────────────────────────────

    @Override
    public boolean hasActiveSession(final String safeSessionId) {
        return sessions.containsKey(safeSessionId);
    }

    @Override
    public String start(final String safeSessionId) {
        sessions.put(safeSessionId, new HotelCreationState());
        log.info("Hotel creation session started — safeSessionId: {}", safeSessionId);
        return """
                I'd love to help you create a new hotel! 🏨
                
                Would you like me to:
                
                🎲 **Randomize it** — I'll generate all the hotel details and rooms automatically
                ✍️  **Customize it** — you provide the details and I'll guide you step by step
                
                Type **"random"** or **"customize"** to get started, or **"cancel"** at any time to stop.
                """;
    }

    @Override
    public String handleMessage(final String managerId,
                                final String safeSessionId,
                                final String userMessage) {
        final HotelCreationState state = sessions.get(safeSessionId);
        if (state == null) {
            log.warn("handleMessage called but no active session found — safeSessionId: {}", safeSessionId);
            return null;
        }

        // Allow cancellation from any phase
        if (isCancellation(userMessage)) {
            sessions.remove(safeSessionId);
            log.info("Hotel creation cancelled by manager — safeSessionId: {}", safeSessionId);
            return "Hotel creation cancelled. Feel free to ask me anything else! 😊";
        }

        return switch (state.getPhase()) {
            case AWAITING_MODE_CHOICE -> handleModeChoice(safeSessionId, state, userMessage);
            case AWAITING_RANDOM_CONFIRMATION -> handleRandomConfirmation(managerId, safeSessionId, state, userMessage);
            case COLLECTING_NAME -> handleCollectName(safeSessionId, state, userMessage);
            case COLLECTING_DESCRIPTION -> handleCollectDescription(safeSessionId, state, userMessage);
            case COLLECTING_CITY -> handleCollectCity(safeSessionId, state, userMessage);
            case COLLECTING_COUNTRY -> handleCollectCountry(safeSessionId, state, userMessage);
            case COLLECTING_ADDRESS -> handleCollectAddress(safeSessionId, state, userMessage);
            case COLLECTING_STAR_RATING -> handleCollectStarRating(safeSessionId, state, userMessage);
            case COLLECTING_CHECKIN_TIME -> handleCollectCheckInTime(safeSessionId, state, userMessage);
            case COLLECTING_CHECKOUT_TIME -> handleCollectCheckOutTime(safeSessionId, state, userMessage);
            case AWAITING_CUSTOM_CONFIRMATION -> handleCustomConfirmation(managerId, safeSessionId, state, userMessage);
        };
    }

    @Override
    public void cancelSession(final String safeSessionId) {
        sessions.remove(safeSessionId);
    }

    // ── Mode choice ───────────────────────────────────────────────────────────

    private String handleModeChoice(final String safeSessionId,
                                    final HotelCreationState state,
                                    final String message) {
        final String lower = message.toLowerCase();
        if (lower.contains("random") || lower.contains("randomize") || lower.contains("generate") || lower.contains(
                "auto")) {
            return generateRandomHotelAndAskConfirmation(safeSessionId, state);
        } else if (lower.contains("custom") || lower.contains("customize") || lower.contains("manual")
                || lower.contains("myself") || lower.contains("provide") || lower.contains("details") || lower.contains(
                "step")) {
            state.setPhase(HotelCreationPhase.COLLECTING_NAME);
            return """
                    Great! Let's set up your hotel step by step. 📝
                    
                    You can type **"cancel"** at any time to stop.
                    
                    **Step 1 / 8 — Hotel Name**
                    What would you like to name your hotel?
                    """;
        } else {
            return """
                    Please choose one of the options:
                    
                    🎲 Type **"random"** to generate everything automatically
                    ✍️  Type **"customize"** to enter the details yourself
                    ❌ Type **"cancel"** to stop
                    """;
        }
    }

    // ── Random mode ───────────────────────────────────────────────────────────

    private String generateRandomHotelAndAskConfirmation(final String safeSessionId,
                                                         final HotelCreationState state) {
        try {
            final AiHotelData generated = chatClient.prompt()
                                                    .user("""
                                                            Generate realistic hotel data for a fictitious hotel on the SmartStay platform.
                                                            Pick a specific real city anywhere in the world — vary the continent each time.
                                                            Create between 3 and 5 rooms with a mix of types.
                                                            All rooms must be available.
                                                            Pricing guidelines (per night): SINGLE $60–100, DOUBLE $100–180, SUITE $200–350, PENTHOUSE $500–900.
                                                            Check-in and check-out times must use HH:MM 24-hour format (e.g. "14:00", "11:00").
                                                            Star rating must be between 1.0 and 5.0 (decimals like 4.5 are fine).
                                                            Room types must be uppercase: SINGLE, DOUBLE, SUITE, or PENTHOUSE.
                                                            Room numbers must be unique positive integers (e.g. 101, 102, 201).
                                                            Include 4–6 relevant hotel amenities as a list of short strings.
                                                            Include 1–3 relevant room amenities for each room.
                                                            """)
                                                    .call()
                                                    .entity(AiHotelData.class);

            state.setGeneratedHotelData(generated);
            state.setPhase(HotelCreationPhase.AWAITING_RANDOM_CONFIRMATION);
            log.info("Random hotel data generated — safeSessionId: {}", safeSessionId);
            return formatRandomHotelPreview(generated);

        } catch (final Exception e) {
            log.error("Failed to generate random hotel data — safeSessionId: {}", safeSessionId, e);
            sessions.remove(safeSessionId);
            return "Sorry, I had trouble generating hotel data right now. 😔 "
                    + "Please try again, or type **\"customize\"** to enter the details yourself.";
        }
    }

    private String formatRandomHotelPreview(final AiHotelData data) {
        final var sb = new StringBuilder();
        sb.append("Here's your randomly generated hotel! ✨\n\n");
        sb.append("🏨 **")
          .append(data.name())
          .append("**\n");
        sb.append("⭐ ")
          .append(data.starRating())
          .append(" stars\n");
        sb.append("📍 ")
          .append(data.city())
          .append(", ")
          .append(data.country())
          .append("\n");
        sb.append("🗺️  ")
          .append(data.address())
          .append("\n\n");
        sb.append("📝 ")
          .append(data.description())
          .append("\n\n");

        if (data.amenities() != null && !data.amenities()
                                             .isEmpty()) {
            sb.append("✨ **Amenities**: ")
              .append(String.join(", ", data.amenities()))
              .append("\n\n");
        }

        sb.append("🕐 Check-in: **")
          .append(data.checkInTime())
          .append("** | Check-out: **")
          .append(data.checkOutTime())
          .append("**\n\n");

        final List<AiRoomData> rooms = data.rooms() != null ? data.rooms() : List.of();
        sb.append("🛏️  **Rooms** (")
          .append(rooms.size())
          .append(" total — all available):\n");
        for (final AiRoomData room : rooms) {
            sb.append("  • Room ")
              .append(room.roomNumber())
              .append(" — ")
              .append(room.type())
              .append(", Floor ")
              .append(room.floor())
              .append(", **$")
              .append(String.format("%.0f", room.pricePerNight()))
              .append("/night**")
              .append(", max ")
              .append(room.maxOccupancy())
              .append(" guests\n");
        }

        sb.append("\n📷 No images will be added — you can upload them later from your Manager Dashboard.\n");
        sb.append("\n**Shall I create this hotel?** Type **\"yes\"** to confirm or **\"no\"** to cancel.");
        return sb.toString();
    }

    private String handleRandomConfirmation(final String managerId,
                                            final String safeSessionId,
                                            final HotelCreationState state,
                                            final String message) {
        if (isConfirmation(message)) {
            return createRandomHotel(managerId, safeSessionId, state);
        }
        // isCancellation is handled before this method is reached, so any other input
        return "Please type **\"yes\"** to create this hotel, or **\"no\"** to cancel.";
    }

    private String createRandomHotel(final String managerId,
                                     final String safeSessionId,
                                     final HotelCreationState state) {
        final AiHotelData data = state.getGeneratedHotelData();
        try {
            final var hotelReq = new CreateHotelClientRequest(data.name(), data.description(), data.city(),
                    data.country(), data.address(), data.starRating(), data.checkInTime(), data.checkOutTime(),
                    data.amenities() != null ? data.amenities() : List.of(), List.of(), Map.of());

            final HotelClientResponse hotel = hotelServiceClient.createHotel(hotelReq, managerId, "HOTEL_MANAGER");
            log.info("Hotel created via AI (random) — hotelId: {}, managerId: {}", hotel.id(), managerId);

            int roomsCreated = 0;
            final List<AiRoomData> rooms = data.rooms() != null ? data.rooms() : List.of();
            for (final AiRoomData room : rooms) {
                try {
                    final String type = sanitizeRoomType(room.type());
                    final var roomReq = new CreateRoomClientRequest(room.roomNumber(), type, room.pricePerNight(),
                            room.maxOccupancy(), room.floor(), room.amenities() != null ? room.amenities() : List.of(),
                            List.of());
                    hotelServiceClient.addRoom(hotel.id(), roomReq, managerId, "HOTEL_MANAGER");
                    roomsCreated++;
                } catch (final Exception re) {
                    log.warn("Failed to create room {} for hotel {} — {}", room.roomNumber(), hotel.id(),
                            re.getMessage());
                }
            }

            sessions.remove(safeSessionId);
            return String.format("""
                    ✅ **%s** has been created successfully!
                    
                    Your hotel is live on SmartStay with **%d room%s** (all available).
                    
                    Head to your **Manager Dashboard** to upload photos, adjust details, or add more rooms.
                    
                    Is there anything else I can help you with?
                    """, hotel.name(), roomsCreated, roomsCreated == 1 ? "" : "s");

        } catch (final Exception e) {
            log.error("Failed to create hotel (random) — managerId: {}", managerId, e);
            sessions.remove(safeSessionId);
            return "❌ I wasn't able to create the hotel right now. "
                    + "Please try again or use the **Add Hotel** button in your Manager Dashboard.";
        }
    }

    // ── Custom mode — step-by-step collection ────────────────────────────────

    private String handleCollectName(final String safeSessionId,
                                     final HotelCreationState state,
                                     final String message) {
        final String name = message.trim();
        if (name.length() < 2) {
            return "Please enter a hotel name with at least 2 characters.";
        }
        state.setName(name);
        state.setPhase(HotelCreationPhase.COLLECTING_DESCRIPTION);
        return """
                **Step 2 / 8 — Description**
                Write a short description of your hotel — what makes it special, its atmosphere, location highlights, etc.
                """;
    }

    private String handleCollectDescription(final String safeSessionId,
                                            final HotelCreationState state,
                                            final String message) {
        final String description = message.trim();
        if (description.length() < 10) {
            return "Please provide a more detailed description (at least 10 characters).";
        }
        state.setDescription(description);
        state.setPhase(HotelCreationPhase.COLLECTING_CITY);
        return "**Step 3 / 8 — City**\nWhat city is your hotel located in?";
    }

    private String handleCollectCity(final String safeSessionId,
                                     final HotelCreationState state,
                                     final String message) {
        final String city = message.trim();
        if (city.isEmpty()) {
            return "Please enter a valid city name.";
        }
        state.setCity(city);
        state.setPhase(HotelCreationPhase.COLLECTING_COUNTRY);
        return "**Step 4 / 8 — Country**\nWhat country is your hotel in?";
    }

    private String handleCollectCountry(final String safeSessionId,
                                        final HotelCreationState state,
                                        final String message) {
        final String country = message.trim();
        if (country.isEmpty()) {
            return "Please enter a valid country name.";
        }
        state.setCountry(country);
        state.setPhase(HotelCreationPhase.COLLECTING_ADDRESS);
        return "**Step 5 / 8 — Address**\nWhat is the full street address of your hotel?";
    }

    private String handleCollectAddress(final String safeSessionId,
                                        final HotelCreationState state,
                                        final String message) {
        final String address = message.trim();
        if (address.isEmpty()) {
            return "Please enter a valid street address.";
        }
        state.setAddress(address);
        state.setPhase(HotelCreationPhase.COLLECTING_STAR_RATING);
        return "**Step 6 / 8 — Star Rating**\nWhat is the star rating of your hotel? (Enter a number from 1 to 5, e.g. **4** or **4.5**)";
    }

    private String handleCollectStarRating(final String safeSessionId,
                                           final HotelCreationState state,
                                           final String message) {
        try {
            // Normalise European comma-decimal (e.g. "4,5") to dot before parsing
            final double rating = Double.parseDouble(message.trim().replace(',', '.'));
            if (rating < 1.0 || rating > 5.0) {
                return "Star rating must be between **1** and **5**. Please enter a value like **3**, **4**, or **4.5**.";
            }
            state.setStarRating(rating);
            state.setPhase(HotelCreationPhase.COLLECTING_CHECKIN_TIME);
            return "**Step 7 / 8 — Check-in Time**\n"
                    + "What time can guests check in? (HH:MM 24-hour format, e.g. **14:00** for 2 PM)";
        } catch (final NumberFormatException e) {
            return "Please enter a valid number for the star rating (e.g. **3**, **4**, or **4.5**).";
        }
    }

    private String handleCollectCheckInTime(final String safeSessionId,
                                            final HotelCreationState state,
                                            final String message) {
        final String time = message.trim();
        if (!isValidTime(time)) {
            return "Please enter a valid time in HH:MM format (e.g. **14:00** for 2 PM, **09:00** for 9 AM).";
        }
        state.setCheckInTime(time);
        state.setPhase(HotelCreationPhase.COLLECTING_CHECKOUT_TIME);
        return "**Step 8 / 8 — Check-out Time**\n"
                + "What time must guests check out? (HH:MM format, e.g. **11:00** for 11 AM)";
    }

    private String handleCollectCheckOutTime(final String safeSessionId,
                                             final HotelCreationState state,
                                             final String message) {
        final String time = message.trim();
        if (!isValidTime(time)) {
            return "Please enter a valid time in HH:MM format (e.g. **11:00** for 11 AM, **12:00** for noon).";
        }
        state.setCheckOutTime(time);
        state.setPhase(HotelCreationPhase.AWAITING_CUSTOM_CONFIRMATION);
        return formatCustomHotelSummary(state);
    }

    private String formatCustomHotelSummary(final HotelCreationState state) {
        return String.format("""
                        Here's a summary of your hotel:
                        
                        🏨 **%s**
                        ⭐ %s stars
                        📍 %s, %s
                        🗺️  %s
                        📝 %s
                        🕐 Check-in: **%s** | Check-out: **%s**
                        📷 No images — you can add them from your Manager Dashboard.
                        ✨ _Amenities and policies can also be added from the Dashboard._
                        
                        **Shall I create this hotel?** Type **"yes"** to confirm or **"no"** to cancel.
                        """, state.getName(), state.getStarRating(), state.getCity(), state.getCountry(), state.getAddress(),
                state.getDescription(), state.getCheckInTime(), state.getCheckOutTime());
    }

    private String handleCustomConfirmation(final String managerId,
                                            final String safeSessionId,
                                            final HotelCreationState state,
                                            final String message) {
        if (isConfirmation(message)) {
            return createCustomHotel(managerId, safeSessionId, state);
        }
        return "Please type **\"yes\"** to create the hotel, or **\"no\"** to cancel.";
    }

    private String createCustomHotel(final String managerId,
                                     final String safeSessionId,
                                     final HotelCreationState state) {
        try {
            final var hotelReq = new CreateHotelClientRequest(state.getName(), state.getDescription(), state.getCity(),
                    state.getCountry(), state.getAddress(), state.getStarRating(), state.getCheckInTime(),
                    state.getCheckOutTime(), List.of(), List.of(), Map.of());

            final HotelClientResponse hotel = hotelServiceClient.createHotel(hotelReq, managerId, "HOTEL_MANAGER");
            log.info("Hotel created via AI (custom) — hotelId: {}, managerId: {}", hotel.id(), managerId);

            sessions.remove(safeSessionId);
            return String.format("""
                    ✅ **%s** has been created successfully!
                    
                    Head to your **Manager Dashboard** to:
                    🛏️  Add rooms
                    📷 Upload photos
                    ✨ Set amenities and policies
                    
                    Is there anything else I can help you with?
                    """, hotel.name());

        } catch (final Exception e) {
            log.error("Failed to create hotel (custom) — managerId: {}", managerId, e);
            sessions.remove(safeSessionId);
            return "❌ I wasn't able to create the hotel right now. "
                    + "Please try again or use the **Add Hotel** button in your Manager Dashboard.";
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isConfirmation(final String msg) {
        final String lower = msg.toLowerCase()
                                .trim();
        return lower.equals("yes") || lower.equals("y") || lower.equals("yes!") || lower.startsWith("yes,")
                || lower.startsWith("yes ") || lower.equals("confirm") || lower.equals("create") || lower.equals("ok")
                || lower.equals("sure") || lower.equals("go ahead") || lower.equals("do it");
    }

    private boolean isCancellation(final String msg) {
        final String lower = msg.toLowerCase()
                                .trim();
        return lower.equals("no") || lower.equals("cancel") || lower.equals("stop") || lower.equals("abort")
                || lower.equals("quit") || lower.contains("never mind") || lower.contains("cancel") || lower.contains(
                "forget it");
    }

    private boolean isValidTime(final String time) {
        return time.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$");
    }

    /**
     * Normalises room type to uppercase and falls back to DOUBLE when the LLM
     * produces a value outside the hotel-service enum.
     */
    private String sanitizeRoomType(final String type) {
        if (type == null) {
            return "DOUBLE";
        }
        final String upper = type.trim()
                                 .toUpperCase();
        return VALID_ROOM_TYPES.contains(upper) ? upper : "DOUBLE";
    }
}
