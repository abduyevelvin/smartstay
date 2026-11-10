package com.smartstay.ai.client.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.smartstay.ai.client.service.HotelIndexingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotelIndexingScheduler {

    private final HotelIndexingService hotelIndexingService;

    @Scheduled(fixedRateString = "${ai.indexing.interval:3600000}")
    public void scheduleReindex() {
        log.info("Scheduled hotel reindex triggered");
        try {
            hotelIndexingService.reindexAll();
            log.info("Scheduled hotel reindex completed successfully");
        } catch (Exception e) {
            // Log and swallow — a failed reindex should not crash the scheduler
            log.error("Scheduled hotel reindex failed: {}", e.getMessage(), e);
        }
    }
}
