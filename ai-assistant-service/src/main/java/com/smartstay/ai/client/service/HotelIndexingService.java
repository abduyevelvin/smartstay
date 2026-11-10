package com.smartstay.ai.client.service;

import java.util.List;

import com.smartstay.ai.client.dto.HotelSummary;
import com.smartstay.ai.client.dto.RoomSummary;

public interface HotelIndexingService {

    void indexHotel(HotelSummary hotel,
                    List<RoomSummary> rooms);

    void reindexAll();
}
