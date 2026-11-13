package com.smartstay.ai.client.service;

import java.util.List;

import org.springframework.ai.document.Document;

import com.smartstay.ai.client.dto.HotelSummary;
import com.smartstay.ai.client.dto.RoomSummary;

public interface HotelIndexingService {

    void indexHotel(HotelSummary hotel,
                    List<RoomSummary> rooms);

    void reindexAll();

    /**
     * Fetches all hotels from hotel-service and returns them as {@link Document}
     * objects suitable for use as LLM context — without writing to the vector store.
     * Used as a fallback when the vector store / embedding model is unavailable.
     */
    List<Document> fetchAllAsDocuments();
}
