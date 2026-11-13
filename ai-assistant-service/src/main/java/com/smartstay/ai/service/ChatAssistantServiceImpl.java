package com.smartstay.ai.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.smartstay.ai.client.service.HotelIndexingService;
import com.smartstay.ai.dto.MessageDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAssistantServiceImpl implements ChatAssistantService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final HotelIndexingService hotelIndexingService;
    private final HotelCreationService hotelCreationService;

    @Override
    public String chat(final String userId,
                       final String sessionId,
                       final String userRole,
                       final String userMessage) {
        // Namespace the memory key so a user can never access another user's history,
        // even if they somehow obtain the other user's sessionId.
        final String safeSessionId = userId + ":" + sessionId;
        log.info("Chat message received — userId: {}, sessionId: {}, role: {}", userId, sessionId, userRole);

        // ── Hotel Creation Flow (HOTEL_MANAGER only) ──────────────────────────
        // Route to the stateful creation wizard when an active session exists.
        if (hotelCreationService.hasActiveSession(safeSessionId)) {
            return hotelCreationService.handleMessage(userId, safeSessionId, userMessage);
        }

        // Detect hotel-creation intent and start the wizard.
        if (isHotelCreationIntent(userMessage)) {
            if ("HOTEL_MANAGER".equals(userRole)) {
                return hotelCreationService.start(safeSessionId);
            } else {
                return "I'm sorry, but hotel creation is only available to hotel managers. " +
                       "If you'd like to become a hotel manager, please contact our admin team. " +
                       "Is there anything else I can help you with? 😊";
            }
        }
        // ─────────────────────────────────────────────────────────────────────

        // RAG step 1: try the vector store (requires an embedding model).
        List<Document> relevantDocs = List.of();
        try {
            final var results = vectorStore.similaritySearch(SearchRequest.builder()
                                                                          .query(userMessage)
                                                                          .topK(5)
                                                                          .build());
            if (results != null && !results.isEmpty()) {
                relevantDocs = results;
                log.debug("Vector store returned {} docs for safeSessionId: {}", relevantDocs.size(), safeSessionId);
            }
        } catch (Exception e) {
            log.warn("Vector store search unavailable ({}), will use direct hotel-service fallback", e.getMessage());
        }

        // RAG step 2: if the vector store yielded nothing (empty store or embedding model
        // down), fall back to fetching all hotels directly from hotel-service.
        // This is always accurate and does not require an embedding model.
        if (relevantDocs.isEmpty()) {
            log.info("Vector store empty — falling back to direct hotel-service fetch for safeSessionId: {}",
                    safeSessionId);
            try {
                relevantDocs = hotelIndexingService.fetchAllAsDocuments();
            } catch (Exception e) {
                log.error("Direct hotel-service fetch also failed for safeSessionId: {} — {}", safeSessionId,
                        e.getMessage(), e);
            }
        }

        // Always call the LLM — never short-circuit with a hardcoded response.
        // When hotel context is available inject it as RAG; when empty the system
        // prompt already tells the model to say no listings match and invite the
        // user to try different criteria. General greetings / questions that don't
        // need hotel data are answered naturally by the model.
        final var promptBuilder = chatClient.prompt()
                                            .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                                                                              .conversationId(safeSessionId)
                                                                              .build());

        log.debug("Retrieved {} hotel docs for safeSessionId: {}", relevantDocs.size(), safeSessionId);

        final String reply;
        try {
            if (relevantDocs.isEmpty()) {
                // No hotel context — call LLM with just the user message.
                // The system prompt handles hotel queries gracefully and lets
                // the model respond normally to greetings / general questions.
                log.debug("No hotel context — calling LLM without RAG context for safeSessionId: {}", safeSessionId);
                reply = promptBuilder.user(userMessage)
                                     .call()
                                     .content();
            } else {
                final var context = relevantDocs.stream()
                                                .map(Document::getText)
                                                .collect(Collectors.joining("\n---\n"));
                reply = promptBuilder.user(u -> u.text("""
                                                         Context from our hotel database (ONLY recommend hotels listed here):
                                                         {context}
                                                         
                                                         User: {message}
                                                         """)
                                                 .param("context", context)
                                                 .param("message", userMessage))
                                     .call()
                                     .content();
            }
        } catch (Exception e) {
            log.error("LLM call failed for userId: {}, sessionId: {} — {}", userId, sessionId, e.getMessage(), e);
            return "I'm having trouble reaching the AI model right now. Please try again in a moment.";
        }

        log.info("Chat reply generated for userId: {}, sessionId: {}", userId, sessionId);
        return reply;
    }

    @Override
    public List<MessageDto> getHistory(final String userId,
                                       final String sessionId) {
        final String safeSessionId = userId + ":" + sessionId;
        log.debug("Retrieving chat history for userId: {}, sessionId: {}", userId, sessionId);

        final var messages = chatMemory.get(safeSessionId);  // namespaced key

        final var history = messages.stream()
                                    .filter(m -> m.getMessageType()
                                            != org.springframework.ai.chat.messages.MessageType.SYSTEM)
                                    .map(m -> new MessageDto(m.getMessageType()
                                                              .getValue(),   // "user" | "assistant"
                                            m.getText()))
                                    .collect(Collectors.toList());

        log.debug("Returning {} messages for userId: {}, sessionId: {}", history.size(), userId, sessionId);
        return history;
    }

    // ── Hotel creation intent detection ───────────────────────────────────────

    /**
     * Returns {@code true} when the user message indicates intent to create/register
     * a new hotel. Checked before any RAG processing so the creation wizard can
     * intercept the message first.
     */
    private boolean isHotelCreationIntent(final String message) {
        final String lower = message.toLowerCase();
        // Must mention "hotel" to avoid false positives on "create booking", etc.
        if (!lower.contains("hotel")) {
            return false;
        }
        return lower.contains("create") || lower.contains("add") || lower.contains("register")
               || lower.contains("new hotel") || lower.contains("make") || lower.contains("build")
               || lower.contains("open") || lower.contains("list my") || lower.contains("setup")
               || lower.contains("set up") || lower.contains("start") || lower.contains("launch");
    }
}
