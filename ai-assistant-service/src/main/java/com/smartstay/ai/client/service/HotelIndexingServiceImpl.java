package com.smartstay.ai.client.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.smartstay.ai.client.HotelServiceClient;
import com.smartstay.ai.client.dto.HotelSearchParams;
import com.smartstay.ai.client.dto.HotelSummary;
import com.smartstay.ai.client.dto.RoomSummary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelIndexingServiceImpl implements HotelIndexingService {

    private final VectorStore vectorStore;
    private final HotelServiceClient hotelServiceClient;

    @Override
    public void indexHotel(final HotelSummary hotel,
                           final List<RoomSummary> rooms) {
        log.debug("Building document for hotel: {} ({})", hotel.name(), hotel.id());
        final var content = buildDocumentText(hotel, rooms);

        // Spring AI 1.0.0: use Document.builder() — no multi-arg constructor
        final var doc = Document.builder()
                                .id(hotel.id())
                                .text(content)
                                .metadata(
                                        Map.of("hotelId", hotel.id(), "city", hotel.city(), "country", hotel.country(),
                                                "starRating", hotel.starRating()))
                                .build();

        vectorStore.add(List.of(doc));
        log.info("Indexed hotel: {} ({}) with {} rooms", hotel.name(), hotel.id(), rooms.size());
    }

    private String buildDocumentText(final HotelSummary hotel,
                                     final List<RoomSummary> rooms) {
        final var roomDesc = rooms.stream()
                                  .map(r -> "%s (max %d guests, $%.2f/night)".formatted(r.id(), r.maxOccupancy(),
                                          r.pricePerNight()))
                                  .collect(Collectors.joining(", "));

        return """
                Hotel: %s
                Location: %s, %s
                Stars: %d
                Description: %s
                Amenities: %s
                Average Rating: %.1f
                Rooms: %s
                """.formatted(hotel.name(), hotel.city(), hotel.country(), hotel.starRating(),
                hotel.description() != null ? hotel.description() : "N/A",
                hotel.amenities() != null ? String.join(", ", hotel.amenities()) : "N/A", hotel.averageRating(),
                roomDesc.isEmpty() ? "No rooms listed" : roomDesc);
    }

    @Override
    public List<Document> fetchAllAsDocuments() {
        log.debug("Fetching all hotels directly from hotel-service (vector-store fallback)");
        final var page = hotelServiceClient.getAllHotels(new HotelSearchParams(null, null, null, 0, 200));
        if (page.content().isEmpty()) {
            log.warn("fetchAllAsDocuments: no hotels returned from hotel-service");
            return List.of();
        }
        return page.content().stream().map(hotel -> {
            final var rooms = hotelServiceClient.getRooms(hotel.id());
            return Document.builder()
                           .id(hotel.id())
                           .text(buildDocumentText(hotel, rooms))
                           .metadata(Map.of("hotelId", hotel.id(), "city", hotel.city(),
                                   "country", hotel.country(), "starRating", hotel.starRating()))
                           .build();
        }).collect(Collectors.toList());
    }

    @Override
    public void reindexAll() {
        log.info("Starting full hotel reindexing...");
        final var page = hotelServiceClient.getAllHotels(new HotelSearchParams(null, null, null, 0, 1000));

        if (page.content()
                .isEmpty()) {
            log.warn("No hotels returned from hotel-service — vector store not updated");
            return;
        }

        log.info("Fetched {} hotels from hotel-service, indexing...", page.content()
                                                                          .size());
        page.content()
            .forEach(hotel -> {
                List<RoomSummary> rooms = hotelServiceClient.getRooms(hotel.id());
                indexHotel(hotel, rooms);
            });
        log.info("Reindexed {} hotels successfully", page.content()
                                                         .size());
    }
}
