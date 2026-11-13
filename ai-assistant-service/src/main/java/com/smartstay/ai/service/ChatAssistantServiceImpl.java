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

    @Override
    public String chat(final String userId,
                       final String sessionId,
                       final String userMessage) {
        // Namespace the memory key so a user can never access another user's history,
        // even if they somehow obtain the other user's sessionId.
        final String safeSessionId = userId + ":" + sessionId;
        log.info("Chat message received — userId: {}, sessionId: {}", userId, sessionId);

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

        // No hotels at all in the system (neither vector store nor hotel-service returned anything).
        if (relevantDocs.isEmpty()) {
            log.debug("No hotel context found anywhere for safeSessionId: {}", safeSessionId);
            return "I'm sorry, I couldn't find any hotels matching your search in our system. "
                    + "Please try different criteria — a different city, star rating, or check back later "
                    + "as new properties are added regularly.";
        }

        // Hotels found — build the RAG prompt and call the LLM.
        final var promptBuilder = chatClient.prompt()
                                            .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                                                                              .conversationId(safeSessionId)
                                                                              .build());

        log.debug("Retrieved {} hotel docs for safeSessionId: {}", relevantDocs.size(), safeSessionId);
        final var context = relevantDocs.stream()
                                        .map(Document::getText)
                                        .collect(Collectors.joining("\n---\n"));

        final String reply;
        try {
            reply = promptBuilder.user(u -> u.text("""
                                                     Context from our hotel database (ONLY recommend hotels listed here):
                                                     {context}
                                                     
                                                     User: {message}
                                                     """)
                                             .param("context", context)
                                             .param("message", userMessage))
                                 .call()
                                 .content();
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
}
