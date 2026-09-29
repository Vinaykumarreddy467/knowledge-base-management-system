package com.kbms.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    public enum Role {
        USER,
        ASSISTANT
    }

    /** Whether the answer was grounded in retrieved context or fell back to "not enough information". */
    public enum Grounding {
        ANSWERED,
        NO_CONTEXT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Lob
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** Citations are assembled by the application, never by the model. */
    @Lob
    @Column(columnDefinition = "text")
    private String citations;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Grounding grounding;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ChatMessage() {}

    public ChatMessage(Long sessionId, Role role, String content, String citations, Grounding grounding) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.citations = citations;
        this.grounding = grounding;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getCitations() {
        return citations;
    }

    public Grounding getGrounding() {
        return grounding;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
