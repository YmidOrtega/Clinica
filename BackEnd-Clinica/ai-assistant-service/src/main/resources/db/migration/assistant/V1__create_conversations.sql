CREATE TABLE assistant.conversations (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid         UUID         NOT NULL,
    version      BIGINT       NOT NULL,
    owner_uuid   UUID         NOT NULL,
    title        VARCHAR(120) NOT NULL,
    status       VARCHAR(10)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    closed_at    TIMESTAMPTZ  NULL,
    CONSTRAINT uk_conversations_uuid UNIQUE (uuid),
    CONSTRAINT chk_conversations_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_conversations_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    CONSTRAINT chk_conversations_title CHECK (length(trim(title)) > 0)
);

CREATE INDEX idx_conversations_owner ON assistant.conversations (owner_uuid, updated_at DESC);

CREATE TABLE assistant.conversation_messages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            UUID        NOT NULL,
    conversation_id BIGINT      NOT NULL REFERENCES assistant.conversations (id),
    position        INT         NOT NULL,
    role            VARCHAR(10) NOT NULL,
    content         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_conversation_messages_uuid UNIQUE (uuid),
    CONSTRAINT uk_conversation_messages_position UNIQUE (conversation_id, position),
    CONSTRAINT chk_conversation_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT chk_conversation_messages_content CHECK (length(content) BETWEEN 1 AND 20000)
);
