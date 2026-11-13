package com.smartstay.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

import com.smartstay.ai.client.service.HotelIndexingService;
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

    @Mock
    private HotelIndexingService hotelIndexingService;

    @Mock
    private HotelCreationService hotelCreationService;

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
        lenient().when(requestSpec.user(anyString()))
                 .thenReturn(requestSpec);
        lenient().when(requestSpec.call())
                 .thenReturn(callSpec);
    }

    private static final String USER_ID = "user-42";
    private static final String SESSION_ID = "session-1";
    // The safe key the service builds internally
    private static final String SAFE_KEY = USER_ID + ":" + SESSION_ID;
    private static final String ROLE_GUEST = "GUEST";
    private static final String ROLE_MANAGER = "HOTEL_MANAGER";

    // ── hotel creation intent — manager ──────────────────────────────────────

    @Test
    void chat_whenManagerSaysCreateHotel_startsCreationWizard() {
        when(hotelCreationService.hasActiveSession(SAFE_KEY)).thenReturn(false);
        when(hotelCreationService.start(SAFE_KEY)).thenReturn("I'd love to help you create a new hotel!");

        String result = chatAssistantService.chat(USER_ID, SESSION_ID, ROLE_MANAGER, "I want to create a hotel");

        assertThat(result).contains("create a new hotel");
        verify(hotelCreationService).start(SAFE_KEY);
    }

    @Test
    void chat_whenGuestSaysCreateHotel_returnsAccessDeniedMessage() {
        when(hotelCreationService.hasActiveSession(SAFE_KEY)).thenReturn(false);

        String result = chatAssistantService.chat(USER_ID, SESSION_ID, ROLE_GUEST, "I want to create a hotel");

        assertThat(result).containsIgnoringCase("only available to hotel managers");
    }

    @Test
    void chat_whenActiveCreationSession_routesToCreationService() {
        when(hotelCreationService.hasActiveSession(SAFE_KEY)).thenReturn(true);
        when(hotelCreationService.handleMessage(USER_ID, SAFE_KEY, "random")).thenReturn("Generating...");

        String result = chatAssistantService.chat(USER_ID, SESSION_ID, ROLE_MANAGER, "random");

        assertThat(result).isEqualTo("Generating...");
        verify(hotelCreationService).handleMessage(USER_ID, SAFE_KEY, "random");
    }

    // ── regular RAG chat ─────────────────────────────────────────────────────

    @Test
    void chat_whenDocsFound_returnsLlmReply() {
        when(hotelCreationService.hasActiveSession(SAFE_KEY)).thenReturn(false);
        var doc = Document.builder()
                          .id("h1")
                          .text("Hotel Hilton details")
                          .build();
        when(vectorStore.similaritySearch((SearchRequest) any())).thenReturn(List.of(doc));
        when(callSpec.content()).thenReturn("The Hilton is a great choice!");

        String result = chatAssistantService.chat(USER_ID, SESSION_ID, ROLE_GUEST, "find me a hotel");

        assertThat(result).isEqualTo("The Hilton is a great choice!");
        verify(vectorStore).similaritySearch((SearchRequest) any());
        verify(requestSpec).advisors(any(Advisor.class));
    }

    @Test
    void chat_whenVectorStoreEmpty_fallsBackToDirectFetch_thenCallsLlm() {
        when(hotelCreationService.hasActiveSession(SAFE_KEY)).thenReturn(false);
        when(vectorStore.similaritySearch((SearchRequest) any())).thenReturn(List.of());
        var doc = Document.builder()
                          .id("h2")
                          .text("Grand Hotel Paris")
                          .build();
        when(hotelIndexingService.fetchAllAsDocuments()).thenReturn(List.of(doc));
        when(callSpec.content()).thenReturn("Grand Hotel Paris is available.");

        String result = chatAssistantService.chat(USER_ID, SESSION_ID, ROLE_GUEST, "any hotels?");

        assertThat(result).isEqualTo("Grand Hotel Paris is available.");
        verify(hotelIndexingService).fetchAllAsDocuments();
    }

    // ── getHistory ───────────────────────────────────────────────────────────

    @Test
    void getHistory_mapsUserAndAssistantMessages() {
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
        String otherSafeKey = "other-user:" + SESSION_ID;
        when(chatMemory.get(SAFE_KEY)).thenReturn(List.of(new UserMessage("private message")));
        when(chatMemory.get(otherSafeKey)).thenReturn(List.of());

        List<MessageDto> userHistory = chatAssistantService.getHistory(USER_ID, SESSION_ID);
        List<MessageDto> otherHistory = chatAssistantService.getHistory("other-user", SESSION_ID);

        assertThat(userHistory).hasSize(1);
        assertThat(otherHistory).isEmpty();
    }
}
