ALTER TABLE auth_history.revisions
    ADD COLUMN revised_by VARCHAR(36) NULL,
    ADD CONSTRAINT chk_revisions_revised_by
        CHECK (revised_by IS NULL OR REGEXP_LIKE(revised_by, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c'));

CREATE TABLE auth_sessions.authorizations (
    id                   VARCHAR(100) NOT NULL,
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name       VARCHAR(100) NOT NULL,
    grant_type           VARCHAR(100) NOT NULL,
    authorized_scopes    VARCHAR(1000) NULL,
    principal            TEXT         NULL,
    attributes           MEDIUMTEXT   NULL,
    state_hash           CHAR(64)     NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,

    CONSTRAINT pk_authorizations PRIMARY KEY (id),
    CONSTRAINT uk_authorizations_state UNIQUE (state_hash),
    CONSTRAINT chk_authorizations_state_hash CHECK (state_hash IS NULL OR REGEXP_LIKE(state_hash, '^[0-9a-f]{64}$', 'c'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_authorizations_principal ON auth_sessions.authorizations (principal_name, created_at);

CREATE TABLE auth_sessions.authorization_tokens (
    authorization_id VARCHAR(100) NOT NULL,
    token_type       VARCHAR(40)  NOT NULL,
    token_class      VARCHAR(200) NOT NULL,
    value_hash       CHAR(64)     NOT NULL,
    issued_at        DATETIME(6)  NULL,
    expires_at       DATETIME(6)  NULL,
    metadata         MEDIUMTEXT   NULL,

    CONSTRAINT pk_authorization_tokens PRIMARY KEY (authorization_id, token_type),
    CONSTRAINT uk_authorization_tokens_value UNIQUE (value_hash),
    CONSTRAINT fk_authorization_tokens_authorization
        FOREIGN KEY (authorization_id) REFERENCES auth_sessions.authorizations (id) ON DELETE CASCADE,
    CONSTRAINT chk_authorization_tokens_value_hash CHECK (REGEXP_LIKE(value_hash, '^[0-9a-f]{64}$', 'c'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_authorization_tokens_expires ON auth_sessions.authorization_tokens (token_type, expires_at);

CREATE TABLE auth_sessions.rotated_refresh_tokens (
    value_hash       CHAR(64)     NOT NULL,
    authorization_id VARCHAR(100) NOT NULL,
    rotated_at       DATETIME(6)  NOT NULL,

    CONSTRAINT pk_rotated_refresh_tokens PRIMARY KEY (value_hash),
    CONSTRAINT chk_rotated_refresh_tokens_value_hash CHECK (REGEXP_LIKE(value_hash, '^[0-9a-f]{64}$', 'c'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_rotated_refresh_tokens_rotated ON auth_sessions.rotated_refresh_tokens (rotated_at);

CREATE TABLE auth_sessions.one_time_tokens (
    id          CHAR(36)    NOT NULL,
    user_uuid   CHAR(36)    NOT NULL,
    purpose     VARCHAR(20) NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,

    CONSTRAINT pk_one_time_tokens PRIMARY KEY (id),
    CONSTRAINT uk_one_time_tokens_hash UNIQUE (token_hash),
    CONSTRAINT chk_one_time_tokens_purpose CHECK (purpose IN ('ACTIVATION', 'PASSWORD_RESET')),
    CONSTRAINT chk_one_time_tokens_hash CHECK (REGEXP_LIKE(token_hash, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT chk_one_time_tokens_expiry CHECK (expires_at > created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_one_time_tokens_user ON auth_sessions.one_time_tokens (user_uuid, purpose);

CREATE TABLE auth_sessions.mail_outbox (
    id              CHAR(36)     NOT NULL,
    kind            VARCHAR(20)  NOT NULL,
    user_uuid       CHAR(36)     NOT NULL,
    status          VARCHAR(10)  NOT NULL,
    attempts        INT          NOT NULL,
    next_attempt_at DATETIME(6)  NOT NULL,
    last_error      VARCHAR(500) NULL,
    created_at      DATETIME(6)  NOT NULL,
    sent_at         DATETIME(6)  NULL,

    CONSTRAINT pk_mail_outbox PRIMARY KEY (id),
    CONSTRAINT chk_mail_outbox_kind CHECK (kind IN ('ACTIVATION', 'PASSWORD_RESET')),
    CONSTRAINT chk_mail_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT chk_mail_outbox_sent CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
    CONSTRAINT chk_mail_outbox_attempts CHECK (attempts >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_mail_outbox_due ON auth_sessions.mail_outbox (status, next_attempt_at);
CREATE INDEX idx_mail_outbox_user ON auth_sessions.mail_outbox (user_uuid, kind, status);

CREATE TABLE auth_sessions.SPRING_SESSION (
    PRIMARY_ID            CHAR(36)     NOT NULL,
    SESSION_ID            CHAR(36)     NOT NULL,
    CREATION_TIME         BIGINT       NOT NULL,
    LAST_ACCESS_TIME      BIGINT       NOT NULL,
    MAX_INACTIVE_INTERVAL INT          NOT NULL,
    EXPIRY_TIME           BIGINT       NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100) NULL,

    CONSTRAINT pk_spring_session PRIMARY KEY (PRIMARY_ID),
    CONSTRAINT uk_spring_session_id UNIQUE (SESSION_ID)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC;

CREATE INDEX idx_spring_session_expiry ON auth_sessions.SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX idx_spring_session_principal ON auth_sessions.SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE auth_sessions.SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BLOB         NOT NULL,

    CONSTRAINT pk_spring_session_attributes PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT fk_spring_session_attributes_session
        FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES auth_sessions.SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC;
