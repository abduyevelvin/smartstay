package com.smartstay.ai.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.smartstay.ai.client.HotelServiceClient;
import com.smartstay.ai.client.dto.RoomSummary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final HotelServiceClient hotelServiceClient;

    @Override
    public String recommend(final String userPreferences) {
        log.info("Recommendation requested for preferences: {}", userPreferences);

        final var relevantDocs = vectorStore.similaritySearch(SearchRequest.builder()
                                                                           .query(userPreferences)
                                                                           .topK(5)
                                                                           .build());

        if (relevantDocs == null || relevantDocs.isEmpty()) {
            log.warn("No relevant hotels found for preferences: {}", userPreferences);
            return "I couldn't find any hotels matching your preferences. "
                    + "Please try different search criteria or check back later.";
        }

        log.debug("Retrieved {} relevant hotel documents from vector store", relevantDocs.size());
        final var context = relevantDocs.stream()
                                        .map(Document::getText)
                                        .collect(Collectors.joining("\n\n---\n\n"));

        return chatClient.prompt()
                         .user(u -> u.text("""
                                             Based on these hotels:
                                             {context}

                                             User request: {preferences}

                                             Recommend the best options and explain why.
                                             """)
                                     .param("context", context)
                                     .param("preferences", userPreferences))
                         .call()
                         .content();
    }

    @Override
    public String summarizeHotel(final String hotelId) {
        log.info("Hotel summary requested for hotelId: {}", hotelId);

        final var hotel = hotelServiceClient.getHotelById(hotelId);
        final List<RoomSummary> rooms = hotelServiceClient.getRooms(hotelId);

        final var context = """
                Hotel: %s
                Location: %s, %s
                Stars: %d
                Description: %s
                Amenities: %s
                Average Rating: %.1f
                Room types available: %s
                """.formatted(
                hotel.name(),
                hotel.city(), hotel.country(),
                hotel.starRating(),
                hotel.description() != null ? hotel.description() : "N/A",
                hotel.amenities() != null ? String.join(", ", hotel.amenities()) : "N/A",
                hotel.averageRating(),
                rooms.stream().map(r -> "$%.0f/night".formatted(r.pricePerNight()))
                              .distinct().collect(Collectors.joining(", "))
        );

        log.debug("Generating summary for hotel: {} ({})", hotel.name(), hotelId);

        return chatClient.prompt()
                         .user(u -> u.text("""
                                             Based on this hotel information:
                                             {context}

                                             Write a concise, engaging 2-3 sentence summary for guests.
                                             Highlight the best features and what makes this hotel special.
                                             """)
                                     .param("context", context))
                         .call()
                         .content();
    }
}
