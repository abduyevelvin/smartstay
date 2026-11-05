package com.smartstay.gateway.controller;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class FallbackController {

    // Handles all HTTP methods — CircuitBreaker forwards the original method
    @RequestMapping("/fallback")
    public ResponseEntity<Map<String, Object>> fallback() {
        log.warn("Circuit breaker fallback triggered — downstream service unavailable");
        return ResponseEntity.status(SERVICE_UNAVAILABLE)
                             .body(Map.of("timestamp", Instant.now()
                                                              .toString(), "status", 503, "error",
                                     "Service Unavailable", "message",
                                     "The service is temporarily unavailable. Please try again later."));
    }
}
