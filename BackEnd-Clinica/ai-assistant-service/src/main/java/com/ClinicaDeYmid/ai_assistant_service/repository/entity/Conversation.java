package com.ClinicaDeYmid.ai_assistant_service.repository.entity;

import com.ClinicaDeYmid.ai_assistant_service.shared.AssistantException;
import com.ClinicaDeYmid.ai_assistant_service.shared.Rules;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {

    public static final int TITLE_LENGTH = 120;

    public enum Status {
        OPEN,
        CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "owner_uuid", nullable = false, updatable = false)
    private UUID ownerUuid;

    @Column(name = "title", nullable = false, length = TITLE_LENGTH)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @OneToMany(mappedBy = "conversation", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("position")
    private List<ConversationMessage> messages = new ArrayList<>();

    protected Conversation() {
    }

    public static Conversation start(UUID ownerUuid, String title, Clock clock) {
        Conversation conversation = new Conversation();
        conversation.uuid = UUID.randomUUID();
        conversation.ownerUuid = Rules.required(ownerUuid, "ownerUuid");
        conversation.title = Rules.requiredText(title, "title", TITLE_LENGTH);
        conversation.status = Status.OPEN;
        conversation.createdAt = Instant.now(clock);
        conversation.updatedAt = conversation.createdAt;
        return conversation;
    }

    public boolean ownedBy(UUID userUuid) {
        return ownerUuid.equals(userUuid);
    }

    public ConversationMessage append(ConversationMessage.Role role, String content, Clock clock) {
        requireOpen();
        ConversationMessage message = ConversationMessage.of(this, messages.size() + 1, role, content,
                Instant.now(clock));
        messages.add(message);
        updatedAt = message.createdAt();
        return message;
    }

    public void close(Clock clock) {
        requireOpen();
        status = Status.CLOSED;
        closedAt = Instant.now(clock);
        updatedAt = closedAt;
    }

    private void requireOpen() {
        if (status != Status.OPEN) {
            throw new AssistantException.ConversationClosed();
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public String title() {
        return title;
    }

    public Status status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public List<ConversationMessage> messages() {
        return List.copyOf(messages);
    }
}
