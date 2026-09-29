package com.kbms.ai;

import com.kbms.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final RagService rag;

    public ChatController(RagService rag) {
        this.rag = rag;
    }

    @PostMapping("/ask")
    public RagService.Answer ask(@Valid @RequestBody AskRequest request) {
        return rag.ask(request.question(), request.sessionId());
    }

    @GetMapping("/sessions")
    public List<RagService.ChatSessionResponse> sessions() {
        return rag.sessions();
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public RagService.ChatSessionResponse createSession(@Valid @RequestBody SessionRequest request) {
        return rag.createSession(request.title());
    }

    @GetMapping("/sessions/{id}")
    public RagService.ChatSessionResponse session(@PathVariable long id) {
        return rag.session(id);
    }

    @GetMapping("/sessions/{id}/messages")
    public List<RagService.ChatMessageResponse> messages(@PathVariable long id) {
        return rag.messages(id);
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable long id) {
        rag.deleteSession(id);
    }

    public record AskRequest(@NotBlank @Size(max = 2000) String question, Long sessionId) {}

    public record SessionRequest(@Size(max = 300) String title) {}
}
