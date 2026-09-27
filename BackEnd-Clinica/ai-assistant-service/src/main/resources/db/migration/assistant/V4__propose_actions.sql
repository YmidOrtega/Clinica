CREATE TABLE assistant.proposed_actions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             UUID          NOT NULL,
    version          BIGINT        NOT NULL,
    conversation_id  BIGINT        NOT NULL REFERENCES assistant.conversations (id),
    owner_uuid       UUID          NOT NULL,
    invoice_uuid     UUID          NOT NULL,
    invoice_number   VARCHAR(24)   NOT NULL,
    kind             VARCHAR(30)   NOT NULL,
    reason           VARCHAR(500)  NOT NULL,
    status           VARCHAR(12)   NOT NULL,
    proposed_at      TIMESTAMPTZ   NOT NULL,
    expires_at       TIMESTAMPTZ   NOT NULL,
    decided_at       TIMESTAMPTZ   NULL,
    outcome          VARCHAR(1000) NULL,
    CONSTRAINT uk_proposed_actions_uuid UNIQUE (uuid),
    CONSTRAINT chk_proposed_actions_kind CHECK (kind IN ('SIGN', 'SEND_TO_DIAN', 'VALIDATE_RIPS')),
    CONSTRAINT chk_proposed_actions_status CHECK (status IN ('PROPOSED', 'EXECUTING', 'DONE', 'FAILED', 'DISCARDED')),
    CONSTRAINT chk_proposed_actions_decided CHECK ((status = 'PROPOSED') = (decided_at IS NULL)),
    CONSTRAINT chk_proposed_actions_expiry CHECK (expires_at > proposed_at)
);

CREATE INDEX idx_proposed_actions_owner ON assistant.proposed_actions (owner_uuid, status, proposed_at DESC);
