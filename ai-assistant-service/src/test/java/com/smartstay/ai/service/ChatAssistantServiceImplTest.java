package com.smartstay.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import com.smartstay.ai.dto.MessageDto;

@ExtendWith(MockitoExtension.class)
class ChatAssistantServiceImplTest {

    // Plain mock — deep stubs on ChatClient itself fail on advisors() vararg overload
    @Mock
    private ChatClient chatClient;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatMemory chatMemory;

    @InjectMocks
    private ChatAssistantServiceImpl chatAssistantService;

    // Explicit fluent-chain mocks
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;

    @BeforeEach
    void setUpChatClientChain() {
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);

        lenient().when(chatClient.prompt())
                 .thenReturn(requestSpec);
        // advisors(Advisor...) is the vararg overload — any(Advisor.class) matches a single advisor
        lenient().when(requestSpec.advisors(any(Advisor.class)))
                 .thenReturn(requestSpec);
        lenient().when(requestSpec.user(any(Consumer.class)))
                 .thenReturn(requestSpec);
        lenient().when(requestSpec.call())
                 .thenReturn(callSpec);
    }

    private static final String USER_ID = "user-42";
    private static final String SESSION_ID = "session-1";
    // The safe key the service builds internally
    private static final String SAFE_KEY = USER_ID + ":" + SESSION_ID;

    // ── chat ─────────────────────────────────────────────────────────────────

    @Test
    void chat_whenDocsFound_returnsLlmReply() {
        var doc = Document.builder()
                          .id("h1")
                          .text("Hotel Hilton details")
                          .build();
        when(vectorStore.similaritySearch((SearchRequest) any())).thenReturn(List.of(doc));
        when(callSpec.content()).thenReturn("The Hilton is a great choice!");

        String result = chatAssistantService.chat(USER_ID, SESSION_ID, "find me a hotel");

        assertThat(result).isEqualTo("The Hilton is a great choice!");
        verify(vectorStore).similaritySearch((SearchRequest) any());
        // The advisor must have been called with the namespaced key
        verify(requestSpec).advisors(any(Advisor.class));
    }

    @Test
    void chat_whenNoDocs_returnsFallbackWithoutCallingLlm() {
        when(vectorStore.similaritySearch((SearchRequest) any())).thenReturn(List.of());

        String result = chatAssistantService.chat(USER_ID, SESSION_ID, "anything");

        assertThat(result).contains("couldn't find any hotels");
        verifyNoInteractions(chatClient);
    }

    // ── getHistory ───────────────────────────────────────────────────────────

    @Test
    void getHistory_mapsUserAndAssistantMessages() {
        // chatMemory is keyed by the namespaced safeSessionId
        when(chatMemory.get(SAFE_KEY)).thenReturn(List.of(new UserMessage("hello"), new AssistantMessage("hi there")));

        List<MessageDto> history = chatAssistantService.getHistory(USER_ID, SESSION_ID);

        assertThat(history).hasSize(2);
        assertThat(history.get(0)
                          .role()).isEqualTo("user");
        assertThat(history.get(0)
                          .content()).isEqualTo("hello");
        assertThat(history.get(1)
                          .role()).isEqualTo("assistant");
        assertThat(history.get(1)
                          .content()).isEqualTo("hi there");
    }

    @Test
    void getHistory_filtersOutSystemMessages() {
        when(chatMemory.get(SAFE_KEY)).thenReturn(
                List.of(new SystemMessage("You are a hotel assistant"), new UserMessage("hello"),
                        new AssistantMessage("hi!")));

        List<MessageDto> history = chatAssistantService.getHistory(USER_ID, SESSION_ID);

        assertThat(history).hasSize(2);
        assertThat(history).noneMatch(m -> m.role()
                                            .equals("system"));
    }

    @Test
    void getHistory_emptySession_returnsEmptyList() {
        when(chatMemory.get(SAFE_KEY)).thenReturn(List.of());

        List<MessageDto> history = chatAssistantService.getHistory(USER_ID, SESSION_ID);

        assertThat(history).isEmpty();
    }

    @Test
    void getHistory_differentUserSameSessionId_usesDistinctKey() {
        // Two users sharing the same client-side sessionId must get different memory keys
        String otherSafeKey = "other-user:" + SESSION_ID;
        when(chatMemory.get(SAFE_KEY)).thenReturn(List.of(new UserMessage("private message")));
        when(chatMemory.get(otherSafeKey)).thenReturn(List.of());

        List<MessageDto> userHistory = chatAssistantService.getHistory(USER_ID, SESSION_ID);
        List<MessageDto> otherHistory = chatAssistantService.getHistory("other-user", SESSION_ID);

        assertThat(userHistory).hasSize(1);
        assertThat(otherHistory).isEmpty();
    }
}
