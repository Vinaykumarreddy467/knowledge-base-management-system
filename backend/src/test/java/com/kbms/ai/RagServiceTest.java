package com.kbms.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbms.audit.AuditService;
import com.kbms.chat.ChatMessage;
import com.kbms.chat.ChatMessageRepository;
import com.kbms.chat.ChatSession;
import com.kbms.chat.ChatSessionRepository;
import com.kbms.common.ApiException;
import com.kbms.config.AppProperties;
import com.kbms.document.DocumentChunk;
import com.kbms.document.SourceType;
import com.kbms.search.SemanticSearchService;
import com.kbms.search.SemanticSearchService.ScoredChunk;
import com.kbms.security.CurrentUser;
import com.kbms.user.AppUser;
import com.kbms.user.Role;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Grounding contract, with the model stubbed: authorised context only, citations assembled from
 * database records, and an explicit refusal whenever the evidence is inadequate or the model
 * cannot be reached.
 */
class RagServiceTest {

    private static final String NO_CONTEXT = "I could not find enough information in the knowledge base to answer that question.";

    private SemanticSearchService search;
    private LlmService llm;
    private ChatSessionRepository sessions;
    private ChatMessageRepository messages;
    private RagService rag;

    @BeforeEach
    void setUp() {
        AppUser viewer = new AppUser("viewer@kbms.local", "hash", "Vee Ewer", Role.VIEWER);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(viewer, null, List.of()));

        search = mock(SemanticSearchService.class);
        llm = mock(LlmService.class);
        sessions = mock(ChatSessionRepository.class);
        messages = mock(ChatMessageRepository.class);
        AppProperties properties = new AppProperties(
                new AppProperties.Security("test-secret-test-secret-test-secret", 60, List.of()),
                new AppProperties.BootstrapAdmin(null, null),
                new AppProperties.Storage("./target/test-uploads"),
                new AppProperties.Documents(List.of("txt")),
                new AppProperties.Chunking(1200, 150),
                new AppProperties.Embedding("ollama", "nomic-embed-text-v2-moe:latest", 768, "http://ollama.test:11434", "", 30),
                new AppProperties.Llm("ollama", "gemma:2b", "http://ollama.test:11434", "", 30),
                new AppProperties.Rag(5, 0.35, 2000, 24000, NO_CONTEXT));
        rag = new RagService(search, llm, properties, sessions, messages, mock(AuditService.class), new ObjectMapper());
    }

    private ScoredChunk hit(String title, String content, float score) {
        return new ScoredChunk(
                new DocumentChunk(
                        SourceType.DOCUMENT, 12L, null, title, "PROCESSED", 0, content, 0, content.length(), "nomic"),
                score);
    }

    @Test
    void answersFromRetrievedContextAndCitesDatabaseRecords() {
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(search.search("How long do refunds take?", 5, null))
                .thenReturn(List.of(hit("Refund policy", "Refunds are issued within 14 days.", 0.72f)));
        when(llm.answer(anyString(), anyString())).thenReturn("Refunds are issued within 14 days of purchase.");
        when(llm.model()).thenReturn("gemma:2b");

        RagService.Answer answer = rag.ask("How long do refunds take?", null);

        assertThat(answer.answer()).isEqualTo("Refunds are issued within 14 days of purchase.");
        assertThat(answer.grounding()).isEqualTo(ChatMessage.Grounding.ANSWERED);
        assertThat(answer.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.sourceType()).isEqualTo("DOCUMENT");
            assertThat(citation.sourceId()).isEqualTo(12L);
            assertThat(citation.title()).isEqualTo("Refund policy");
            assertThat(citation.score()).isEqualTo(0.72);
        });

        // The model only ever sees delimited retrieved text, never the database ids.
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llm).answer(system.capture(), user.capture());
        assertThat(user.getValue()).contains("Refunds are issued within 14 days.");
        assertThat(user.getValue()).contains("<<<SOURCE 1>>>");
        assertThat(system.getValue()).contains("Answer only from the CONTEXT");
    }

    @Test
    void returnsTheInsufficiencyMessageWithoutCallingTheModelWhenEvidenceIsInadequate() {
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(search.search("What is the parental leave policy?", 5, null)).thenReturn(List.of());

        RagService.Answer answer = rag.ask("What is the parental leave policy?", null);

        assertThat(answer.answer()).isEqualTo(NO_CONTEXT);
        assertThat(answer.grounding()).isEqualTo(ChatMessage.Grounding.NO_CONTEXT);
        assertThat(answer.citations()).isEmpty();
        verify(llm, never()).answer(anyString(), anyString());
    }

    @Test
    void normalisesAModelRefusalAndDropsItsCitations() {
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(search.search("secret?", 5, null))
                .thenReturn(List.of(hit("Weak match", "Unrelated text.", 0.36f)));
        when(llm.answer(anyString(), anyString()))
                .thenReturn("I do not have enough information in the provided context to answer that.");
        when(llm.model()).thenReturn("gemma:2b");

        RagService.Answer answer = rag.ask("secret?", null);

        assertThat(answer.answer()).isEqualTo(NO_CONTEXT);
        assertThat(answer.grounding()).isEqualTo(ChatMessage.Grounding.NO_CONTEXT);
        assertThat(answer.citations()).isEmpty();
    }

    @Test
    void surfacesAProviderFailureInsteadOfInventingAnAnswer() {
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(search.search("anything", 5, null)).thenReturn(List.of(hit("Doc", "Some content.", 0.6f)));
        when(llm.answer(anyString(), anyString()))
                .thenThrow(ApiException.unavailable("Ollama is not reachable at http://localhost:11434"));

        assertThatThrownBy(() -> rag.ask("anything", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Ollama is not reachable");

        // No assistant message is persisted when the model could not be reached.
        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages, org.mockito.Mockito.times(1)).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(ChatMessage.Role.USER);
    }

    @Test
    void refusesToReadOrWriteSomeoneElsesSession() {
        when(sessions.findById(3L)).thenReturn(Optional.of(new ChatSession(999L, "not yours")));

        assertThatThrownBy(() -> rag.ask("q", 3L)).isInstanceOf(ApiException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> rag.messages(3L)).isInstanceOf(ApiException.class).hasMessageContaining("not found");
    }

    @Test
    void singleSessionLookupIsRefusedForANonOwnerExactlyLikeTheOtherRoutes() {
        when(sessions.findById(3L)).thenReturn(Optional.of(new ChatSession(999L, "not yours")));

        assertThatThrownBy(() -> rag.session(3L)).isInstanceOf(ApiException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> rag.messages(3L)).isInstanceOf(ApiException.class).hasMessageContaining("not found");
    }

    @Test
    void singleSessionLookupCannotDistinguishSomeoneElsesSessionFromAMissingOne() {
        when(sessions.findById(3L)).thenReturn(Optional.of(new ChatSession(999L, "not yours")));
        when(sessions.findById(4242L)).thenReturn(Optional.empty());

        // Identical message: a guessed id must not reveal whether the session exists.
        assertThatThrownBy(() -> rag.session(3L)).hasMessage("Chat session 3 was not found");
        assertThatThrownBy(() -> rag.session(4242L)).hasMessage("Chat session 4242 was not found");
        assertThat(catchThrowable(() -> rag.session(3L))
                        .getMessage().replaceAll("\\d+", "#"))
                .isEqualTo(catchThrowable(() -> rag.session(4242L))
                        .getMessage().replaceAll("\\d+", "#"));
    }

    @Test
    void rejectsAnEmptyQuestion() {
        assertThatThrownBy(() -> rag.ask("   ", null)).isInstanceOf(ApiException.class).hasMessageContaining("empty");
    }

    @Test
    void persistsCitationsSoHistoryReplaysTheSameSources() {
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(search.search("q", 5, null)).thenReturn(List.of(hit("Refund policy", "14 days", 0.8f)));
        when(llm.answer(anyString(), anyString())).thenReturn("14 days");
        when(llm.model()).thenReturn("gemma:2b");

        rag.ask("q", null);

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages, org.mockito.Mockito.times(2)).save(saved.capture());
        ChatMessage assistant = saved.getAllValues().get(1);
        assertThat(assistant.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(assistant.getCitations()).contains("Refund policy");
    }

    @Test
    void currentUserIsRequired() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> rag.ask("q", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Authentication is required");
    }
}
