package com.smartstay.ai.client.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import com.smartstay.ai.client.HotelServiceClient;
import com.smartstay.ai.client.dto.HotelPage;
import com.smartstay.ai.client.dto.HotelSummary;
import com.smartstay.ai.client.dto.RoomSummary;

@ExtendWith(MockitoExtension.class)
class HotelIndexingServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private HotelServiceClient hotelServiceClient;

    @InjectMocks
    private HotelIndexingServiceImpl hotelIndexingService;

    @Test
    void indexHotel_addsDocumentWithCorrectIdAndContent() {
        var hotel = new HotelSummary("h1", "Hilton Baku", "Luxury hotel", "Baku", "Azerbaijan", 5,
                List.of("Pool", "Spa"), 4.9);
        var room = new RoomSummary("r1", "h1", 101, 200.0, 2, true);

        hotelIndexingService.indexHotel(hotel, List.of(room));

        verify(vectorStore).add(argThat(docs -> docs.size() == 1 && docs.get(0)
                                                                        .getId()
                                                                        .equals("h1") && docs.get(0)
                                                                                             .getText()
                                                                                             .contains("Hilton Baku")
                && docs.get(0)
                       .getText()
                       .contains("Baku") && docs.get(0)
                                                .getMetadata()
                                                .get("city")
                                                .equals("Baku")));
    }

    @Test
    void reindexAll_indexesEveryHotelFromPage() {
        var hotel1 = new HotelSummary("h1", "Hilton", "Luxury", "Baku", "Azerbaijan", 5, List.of(), 4.8);
        var hotel2 = new HotelSummary("h2", "Marriott", "Modern", "Baku", "Azerbaijan", 4, List.of(), 4.5);
        var page = new HotelPage(List.of(hotel1, hotel2), 1, 2L);
        var rooms = List.of(new RoomSummary("r1", "h1", 101, 150.0, 2, true));

        when(hotelServiceClient.getAllHotels(any())).thenReturn(page);
        when(hotelServiceClient.getRooms(any())).thenReturn(rooms);

        hotelIndexingService.reindexAll();

        // one vectorStore.add() call per hotel
        verify(vectorStore, times(2)).add(any());
        verify(hotelServiceClient).getRooms("h1");
        verify(hotelServiceClient).getRooms("h2");
    }

    @Test
    void reindexAll_whenPageIsEmpty_doesNotCallVectorStore() {
        var emptyPage = new HotelPage(List.of(), 0, 0L);
        when(hotelServiceClient.getAllHotels(any())).thenReturn(emptyPage);

        hotelIndexingService.reindexAll();

        verifyNoInteractions(vectorStore);
    }
}
