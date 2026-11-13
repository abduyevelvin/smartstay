package com.smartstay.ai.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.smartstay.ai.dto.MessageDto;
import com.smartstay.ai.service.ChatAssistantService;
import com.smartstay.ai.service.RecommendationService;

@WebMvcTest(AiController.class)
class AiControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private ChatAssistantService chatAssistantService;

    // ── POST /recommend ───────────────────────────────────────────────────────

    @Test
    void recommend_validRequest_returns200WithRecommendation() throws Exception {
        when(recommendationService.recommend("beachfront hotel in Baku")).thenReturn(
                "I recommend the Hilton Baku — great pool and location.");

        mockMvc.perform(post("/api/v1/ai/recommend").contentType(MediaType.APPLICATION_JSON)
                                                    .content("""
                                                            {"preferences":"beachfront hotel in Baku"}
                                                            """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.recommendation").value("I recommend the Hilton Baku — great pool and location."));
    }

    @Test
    void recommend_blankPreferences_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/recommend").contentType(MediaType.APPLICATION_JSON)
                                                    .content("""
                                                            {"preferences":""}
                                                            """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void recommend_missingPreferencesField_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/recommend").contentType(MediaType.APPLICATION_JSON)
                                                    .content("{}"))
               .andExpect(status().isBadRequest());
    }

    // ── POST /chat ────────────────────────────────────────────────────────────

    @Test
    void chat_validRequest_returns200WithReplyAndSessionId() throws Exception {
        when(chatAssistantService.chat("user-1", "session-abc", "GUEST", "find me a hotel")).thenReturn(
                "The Marriott is a great choice!");

        mockMvc.perform(post("/api/v1/ai/chat").header("X-User-Id", "user-1")
                                               .header("X-User-Role", "GUEST")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content("""
                                                       {"sessionId":"session-abc","message":"find me a hotel"}
                                                       """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.reply").value("The Marriott is a great choice!"))
               .andExpect(jsonPath("$.sessionId").value("session-abc"));
    }

    @Test
    void chat_managerCreatesHotel_returns200() throws Exception {
        when(chatAssistantService.chat("mgr-1", "session-xyz", "HOTEL_MANAGER", "create a hotel")).thenReturn(
                "I'd love to help you create a new hotel!");

        mockMvc.perform(post("/api/v1/ai/chat").header("X-User-Id", "mgr-1")
                                               .header("X-User-Role", "HOTEL_MANAGER")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content("""
                                                       {"sessionId":"session-xyz","message":"create a hotel"}
                                                       """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.reply").value("I'd love to help you create a new hotel!"));
    }

    @Test
    void chat_missingUserIdHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                       // no X-User-Id header
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("""
                               {"sessionId":"session-abc","message":"find me a hotel"}
                               """))
               .andExpect(status().isBadRequest());
    }

    @Test
    void chat_blankSessionId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat").header("X-User-Id", "user-1")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content("""
                                                       {"sessionId":"","message":"find me a hotel"}
                                                       """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void chat_blankMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat").header("X-User-Id", "user-1")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content("""
                                                       {"sessionId":"session-abc","message":""}
                                                       """))
               .andExpect(status().isBadRequest());
    }

    // ── GET /chat/{sessionId}/history ─────────────────────────────────────────

    @Test
    void history_existingSession_returns200WithMessages() throws Exception {
        when(chatAssistantService.getHistory("user-1", "session-abc")).thenReturn(
                List.of(new MessageDto("user", "find me a hotel"),
                        new MessageDto("assistant", "The Marriott is a great choice!")));

        mockMvc.perform(get("/api/v1/ai/chat/session-abc/history").header("X-User-Id", "user-1"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray())
               .andExpect(jsonPath("$.length()").value(2))
               .andExpect(jsonPath("$[0].role").value("user"))
               .andExpect(jsonPath("$[0].content").value("find me a hotel"))
               .andExpect(jsonPath("$[1].role").value("assistant"));
    }

    @Test
    void history_unknownSession_returns200WithEmptyList() throws Exception {
        when(chatAssistantService.getHistory("user-1", "unknown")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/ai/chat/unknown/history").header("X-User-Id", "user-1"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray())
               .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void history_missingUserIdHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/ai/chat/session-abc/history"))
               // no X-User-Id header
               .andExpect(status().isBadRequest());
    }

    // ── POST /hotels/{hotelId}/summary ────────────────────────────────────────

    @Test
    void summary_validHotelId_returns200WithSummary() throws Exception {
        when(recommendationService.summarizeHotel("h1")).thenReturn("A stunning 5-star retreat in the heart of Baku.");

        mockMvc.perform(post("/api/v1/ai/hotels/h1/summary"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.hotelId").value("h1"))
               .andExpect(jsonPath("$.summary").value("A stunning 5-star retreat in the heart of Baku."));
    }

    @Test
    void summary_serviceThrows_returns500() throws Exception {
        when(recommendationService.summarizeHotel(anyString())).thenThrow(new RuntimeException("Hotel not found"));

        mockMvc.perform(post("/api/v1/ai/hotels/bad-id/summary"))
               .andExpect(status().isInternalServerError())
               .andExpect(jsonPath("$.status").value(500));
    }
}
