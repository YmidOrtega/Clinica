ALTER TABLE users
    ADD COLUMN second_factor_state        VARCHAR(20)  NOT NULL DEFAULT 'NOT_ENROLLED' AFTER password_changed_at,
    ADD COLUMN second_factor_enrolled_at  DATETIME(6)  NULL AFTER second_factor_state,
    ADD COLUMN second_factor_reset_reason VARCHAR(500) NULL AFTER second_factor_enrolled_at,
    ADD COLUMN second_factor_reset_by     CHAR(36)     NULL AFTER second_factor_reset_reason,
    ADD COLUMN second_factor_reset_at     DATETIME(6)  NULL AFTER second_factor_reset_by,
    ADD CONSTRAINT chk_users_second_factor_state CHECK (second_factor_state IN ('NOT_ENROLLED', 'TOTP_ENROLLED')),
    ADD CONSTRAINT chk_users_second_factor_enrollment
        CHECK ((second_factor_state = 'TOTP_ENROLLED') = (second_factor_enrolled_at IS NOT NULL)),
    ADD CONSTRAINT chk_users_second_factor_reset
        CHECK ((second_factor_reset_at IS NULL AND second_factor_reset_by IS NULL AND second_factor_reset_reason IS NULL)
            OR (second_factor_reset_at IS NOT NULL AND second_factor_reset_by IS NOT NULL
                    AND CHAR_LENGTH(second_factor_reset_reason) >= 10)),
    ADD CONSTRAINT chk_users_second_factor_needs_credential
        CHECK (second_factor_state = 'NOT_ENROLLED' OR credential_state <> 'NOT_SET');

ALTER TABLE users ALTER COLUMN second_factor_state DROP DEFAULT;

ALTER TABLE auth_history.users_aud
    ADD COLUMN second_factor_state        VARCHAR(20)  NULL,
    ADD COLUMN second_factor_enrolled_at  DATETIME(6)  NULL,
    ADD COLUMN second_factor_reset_reason VARCHAR(500) NULL,
    ADD COLUMN second_factor_reset_by     CHAR(36)     NULL,
    ADD COLUMN second_factor_reset_at     DATETIME(6)  NULL;

CREATE TABLE recovery_codes (
    id         CHAR(36)    NOT NULL,
    user_uuid  CHAR(36)    NOT NULL,
    code_hash  CHAR(64)    NOT NULL,
    created_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,

    CONSTRAINT pk_recovery_codes PRIMARY KEY (id),
    CONSTRAINT uk_recovery_codes_hash UNIQUE (code_hash),
    CONSTRAINT fk_recovery_codes_user FOREIGN KEY (user_uuid) REFERENCES users (uuid),
    CONSTRAINT chk_recovery_codes_hash CHECK (REGEXP_LIKE(code_hash, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT chk_recovery_codes_single_use CHECK (used_at IS NULL OR revoked_at IS NULL)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_recovery_codes_user ON recovery_codes (user_uuid, used_at, revoked_at);
