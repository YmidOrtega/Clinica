package com.ClinicaDeYmid.ai_assistant_service.repository.entity;

import com.ClinicaDeYmid.ai_assistant_service.shared.AssistantException;
import com.ClinicaDeYmid.ai_assistant_service.shared.Rules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_messages")
public class ConversationMessage {

    public static final int CONTENT_LENGTH = 20000;

    public enum Role {
        USER,
        ASSISTANT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false, length = 10)
    private Role role;

    @Column(name = "content", nullable = false, updatable = false, length = CONTENT_LENGTH)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ConversationMessage() {
    }

    static ConversationMessage of(Conversation conversation, int position, Role role, String content, Instant at) {
        ConversationMessage message = new ConversationMessage();
        message.uuid = UUID.randomUUID();
        message.conversation = conversation;
        message.position = position;
        message.role = Rules.required(role, "role");
        if (content == null || content.isBlank()) {
            throw new AssistantException.InvalidData("content", "es obligatorio");
        }
        if (content.length() > CONTENT_LENGTH) {
            throw new AssistantException.InvalidData("content", "no puede superar " + CONTENT_LENGTH + " caracteres");
        }
        message.content = content;
        message.createdAt = at;
        return message;
    }

    public UUID uuid() {
        return uuid;
    }

    public int position() {
        return position;
    }

    public Role role() {
        return role;
    }

    public String content() {
        return content;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
