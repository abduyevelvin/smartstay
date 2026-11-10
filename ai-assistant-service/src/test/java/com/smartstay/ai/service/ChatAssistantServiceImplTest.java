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

    // ── chat ─────────────────────────────────────────────────────────────────

    @Test
    void chat_whenDocsFound_returnsLlmReply() {
        var doc = Document.builder()
                          .id("h1")
                          .text("Hotel Hilton details")
                          .build();
        when(vectorStore.similaritySearch((SearchRequest) any())).thenReturn(List.of(doc));
        when(callSpec.content()).thenReturn("The Hilton is a great choice!");

        String result = chatAssistantService.chat("session-1", "find me a hotel");

        assertThat(result).isEqualTo("The Hilton is a great choice!");
        verify(vectorStore).similaritySearch((SearchRequest) any());
        verify(requestSpec).advisors(any(Advisor.class));
    }

    @Test
    void chat_whenNoDocs_returnsFallbackWithoutCallingLlm() {
        when(vectorStore.similaritySearch((SearchRequest) any())).thenReturn(List.of());

        String result = chatAssistantService.chat("session-1", "anything");

        assertThat(result).contains("couldn't find any hotels");
        verifyNoInteractions(chatClient);
    }

    // ── getHistory ───────────────────────────────────────────────────────────

    @Test
    void getHistory_mapsUserAndAssistantMessages() {
        when(chatMemory.get("session-1")).thenReturn(
                List.of(new UserMessage("hello"), new AssistantMessage("hi there")));

        List<MessageDto> history = chatAssistantService.getHistory("session-1");

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
        when(chatMemory.get("session-1")).thenReturn(
                List.of(new SystemMessage("You are a hotel assistant"), new UserMessage("hello"),
                        new AssistantMessage("hi!")));

        List<MessageDto> history = chatAssistantService.getHistory("session-1");

        assertThat(history).hasSize(2);
        assertThat(history).noneMatch(m -> m.role()
                                            .equals("system"));
    }

    @Test
    void getHistory_emptySession_returnsEmptyList() {
        when(chatMemory.get("unknown-session")).thenReturn(List.of());

        List<MessageDto> history = chatAssistantService.getHistory("unknown-session");

        assertThat(history).isEmpty();
    }
}
