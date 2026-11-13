package com.smartstay.ai.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartstay.ai.dto.ChatRequest;
import com.smartstay.ai.dto.MessageDto;
import com.smartstay.ai.dto.RecommendRequest;
import com.smartstay.ai.service.ChatAssistantService;
import com.smartstay.ai.service.RecommendationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final RecommendationService recommendationService;
    private final ChatAssistantService chatAssistantService;

    // POST /api/v1/ai/recommend
    // Body: { "preferences": "beachfront hotel in Baku with pool" }
    @PostMapping("/recommend")
    public ResponseEntity<Map<String, String>> recommend(@Valid @RequestBody final RecommendRequest request) {
        log.info("POST /recommend — preferences: {}", request.preferences());
        final String result = recommendationService.recommend(request.preferences());
        return ResponseEntity.ok(Map.of("recommendation", result));
    }

    // POST /api/v1/ai/chat
    // Body: { "sessionId": "abc123", "message": "Which hotels have a pool?" }
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestHeader("X-User-Id") final String userId,
                                                    @Valid @RequestBody final ChatRequest request) {
        log.info("POST /chat — userId: {}, sessionId: {}", userId, request.sessionId());
        final String reply = chatAssistantService.chat(userId, request.sessionId(), request.message());
        return ResponseEntity.ok(Map.of("reply", reply, "sessionId", request.sessionId()));
    }

    // GET /api/v1/ai/chat/{sessionId}/history
    @GetMapping("/chat/{sessionId}/history")
    public ResponseEntity<List<MessageDto>> history(@RequestHeader("X-User-Id") final String userId,
                                                    @PathVariable final String sessionId) {
        log.info("GET /chat/{}/history — userId: {}", sessionId, userId);
        final List<MessageDto> history = chatAssistantService.getHistory(userId, sessionId);
        return ResponseEntity.ok(history);
    }

    // POST /api/v1/ai/hotels/{hotelId}/summary
    @PostMapping("/hotels/{hotelId}/summary")
    public ResponseEntity<Map<String, String>> summary(@PathVariable final String hotelId) {
        log.info("POST /hotels/{}/summary", hotelId);
        final String summary = recommendationService.summarizeHotel(hotelId);
        return ResponseEntity.ok(Map.of("hotelId", hotelId, "summary", summary));
    }
}
