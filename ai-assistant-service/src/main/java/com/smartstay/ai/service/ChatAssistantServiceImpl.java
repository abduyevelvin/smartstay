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

    @Override
    public String chat(final String userId,
                       final String sessionId,
                       final String userMessage) {
        // Namespace the memory key so a user can never access another user's history,
        // even if they somehow obtain the other user's sessionId.
        final String safeSessionId = userId + ":" + sessionId;
        log.info("Chat message received — userId: {}, sessionId: {}", userId, sessionId);

        final var relevantDocs = vectorStore.similaritySearch(SearchRequest.builder()
                                                                           .query(userMessage)
                                                                           .topK(3)
                                                                           .build());

        if (relevantDocs == null || relevantDocs.isEmpty()) {
            log.warn("No relevant hotel context found for userId: {}, sessionId: {}", userId, sessionId);
            return "I couldn't find any hotels matching your request. "
                    + "Please try different search criteria or check back later.";
        }

        log.debug("Retrieved {} relevant hotel documents for safeSessionId: {}", relevantDocs.size(), safeSessionId);

        final var context = relevantDocs.stream()
                                        .map(Document::getText)
                                        .collect(Collectors.joining("\n---\n"));

        // Spring AI 1.0.0: constructor is private — must use builder
        // maxMessages is configured on MessageWindowChatMemory bean in AiConfig
        final var reply = chatClient.prompt()
                                    .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                                                                      .conversationId(safeSessionId)
                                                                      .build())
                                    .user(u -> u.text("""
                                                        Context from our hotel database:
                                                        {context}
                                                        
                                                        User: {message}
                                                        """)
                                                .param("context", context)
                                                .param("message", userMessage))
                                    .call()
                                    .content();

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
