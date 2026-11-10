package com.smartstay.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import com.smartstay.ai.client.HotelServiceClient;
import com.smartstay.ai.client.dto.HotelSummary;
import com.smartstay.ai.client.dto.RoomSummary;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private HotelServiceClient hotelServiceClient;

    @InjectMocks
    private RecommendationServiceImpl recommendationService;

    // ── recommend ────────────────────────────────────────────────────────────

    @Test
    void recommend_whenDocsFound_returnsLlmResponse() {
        var doc = Document.builder()
                          .id("h1")
                          .text("Hotel Hilton, Baku, 5 stars")
                          .build();
        when(vectorStore.similaritySearch((SearchRequest) any())).thenReturn(List.of(doc));
        when(chatClient.prompt()
                       .user(any(Consumer.class))
                       .call()
                       .content()).thenReturn("I recommend the Hilton Baku");

        String result = recommendationService.recommend("beachfront hotel in Baku");

        assertThat(result).isEqualTo("I recommend the Hilton Baku");
        verify(vectorStore).similaritySearch((SearchRequest) any());
    }

    @Test
    void recommend_whenNoDocs_returnsFallbackWithoutCallingLlm() {
        when(vectorStore.similaritySearch((SearchRequest) any())).thenReturn(List.of());

        String result = recommendationService.recommend("any preference");

        assertThat(result).contains("couldn't find any hotels");
        verifyNoInteractions(chatClient);
    }

    @Test
    void recommend_whenNullDocs_returnsFallbackWithoutCallingLlm() {
        when(vectorStore.similaritySearch((SearchRequest) any())).thenReturn(null);

        String result = recommendationService.recommend("any preference");

        assertThat(result).contains("couldn't find any hotels");
        verifyNoInteractions(chatClient);
    }

    // ── summarizeHotel ───────────────────────────────────────────────────────

    @Test
    void summarizeHotel_success_fetchesHotelAndGeneratesSummary() {
        var hotel = new HotelSummary("h1", "Hilton Baku", "Luxury hotel", "Baku", "Azerbaijan", 5,
                List.of("Pool", "Spa"), 4.9);
        var room = new RoomSummary("r1", "h1", 101, 200.0, 2, true);

        when(hotelServiceClient.getHotelById("h1")).thenReturn(hotel);
        when(hotelServiceClient.getRooms("h1")).thenReturn(List.of(room));
        when(chatClient.prompt()
                       .user(any(Consumer.class))
                       .call()
                       .content()).thenReturn("A stunning 5-star retreat in the heart of Baku.");

        String result = recommendationService.summarizeHotel("h1");

        assertThat(result).isEqualTo("A stunning 5-star retreat in the heart of Baku.");
        verify(hotelServiceClient).getHotelById("h1");
        verify(hotelServiceClient).getRooms("h1");
    }

    @Test
    void summarizeHotel_noRooms_stillGeneratesSummary() {
        var hotel = new HotelSummary("h1", "Hilton Baku", "Nice hotel", "Baku", "Azerbaijan", 4, List.of(), 4.5);

        when(hotelServiceClient.getHotelById("h1")).thenReturn(hotel);
        when(hotelServiceClient.getRooms("h1")).thenReturn(List.of());
        when(chatClient.prompt()
                       .user(any(Consumer.class))
                       .call()
                       .content()).thenReturn("A great 4-star hotel.");

        String result = recommendationService.summarizeHotel("h1");

        assertThat(result).isEqualTo("A great 4-star hotel.");
    }
}
