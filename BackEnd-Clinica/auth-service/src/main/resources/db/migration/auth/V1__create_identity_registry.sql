CREATE TABLE users (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                   CHAR(36)     NOT NULL,
    version                BIGINT       NOT NULL,
    email                  VARCHAR(254) NOT NULL,
    full_name              VARCHAR(150) NOT NULL,
    role                   VARCHAR(20)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    status_reason          VARCHAR(500) NULL,
    status_changed_by      CHAR(36)     NULL,
    status_changed_by_role VARCHAR(20)  NULL,
    status_changed_at      DATETIME(6)  NOT NULL,
    password_hash          VARCHAR(255) NULL,
    credential_state       VARCHAR(20)  NOT NULL,
    credential_reason      VARCHAR(500) NULL,
    password_changed_at    DATETIME(6)  NULL,
    tokens_not_before      DATETIME(6)  NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_uuid UNIQUE (uuid),
    CONSTRAINT uk_users_email UNIQUE (email),

    CONSTRAINT chk_users_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_users_version CHECK (version >= 0),
    CONSTRAINT chk_users_email
        CHECK (REGEXP_LIKE(email, '^[a-z0-9.!#$%&''*+/=?^_`{|}~-]{1,64}@[a-z0-9-]+([.][a-z0-9-]+)+$', 'c')),
    CONSTRAINT chk_users_full_name CHECK (CHAR_LENGTH(TRIM(full_name)) >= 3),
    CONSTRAINT chk_users_role
        CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'MEDICAL_RECORDS')),
    CONSTRAINT chk_users_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
    CONSTRAINT chk_users_status_details
        CHECK ((status IN ('PENDING_ACTIVATION', 'ACTIVE')
                    AND status_reason IS NULL AND status_changed_by IS NULL AND status_changed_by_role IS NULL)
            OR (status IN ('SUSPENDED', 'DEACTIVATED')
                    AND CHAR_LENGTH(status_reason) >= 10 AND status_changed_by IS NOT NULL AND status_changed_by_role IS NOT NULL)),
    CONSTRAINT chk_users_status_changed_by_role
        CHECK (status_changed_by_role IS NULL OR status_changed_by_role IN ('SUPER_ADMIN', 'ADMIN')),
    CONSTRAINT chk_users_credential_state CHECK (credential_state IN ('NOT_SET', 'CURRENT', 'CHANGE_REQUIRED')),
    CONSTRAINT chk_users_credential
        CHECK ((credential_state = 'NOT_SET' AND password_hash IS NULL AND password_changed_at IS NULL AND credential_reason IS NULL)
            OR (credential_state = 'CURRENT' AND password_hash IS NOT NULL AND password_changed_at IS NOT NULL
                    AND credential_reason IS NULL)
            OR (credential_state = 'CHANGE_REQUIRED' AND password_hash IS NOT NULL AND password_changed_at IS NOT NULL
                    AND CHAR_LENGTH(credential_reason) >= 10)),
    CONSTRAINT chk_users_password_hash
        CHECK (password_hash IS NULL OR password_hash LIKE '$argon2id$v=19$m=%'),
    CONSTRAINT chk_users_pending_without_credential
        CHECK (status <> 'PENDING_ACTIVATION' OR credential_state = 'NOT_SET'),
    CONSTRAINT chk_users_active_with_credential
        CHECK (status <> 'ACTIVE' OR credential_state <> 'NOT_SET')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_users_role_status ON users (role, status);

CREATE DATABASE IF NOT EXISTS auth_history
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE auth_history.revisions (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    revised_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_revisions PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE auth_history.users_aud (
    id                     BIGINT       NOT NULL,
    rev                    BIGINT       NOT NULL,
    revtype                TINYINT      NOT NULL,
    uuid                   CHAR(36)     NULL,
    email                  VARCHAR(254) NULL,
    full_name              VARCHAR(150) NULL,
    role                   VARCHAR(20)  NULL,
    status                 VARCHAR(20)  NULL,
    status_reason          VARCHAR(500) NULL,
    status_changed_by      CHAR(36)     NULL,
    status_changed_by_role VARCHAR(20)  NULL,
    status_changed_at      DATETIME(6)  NULL,
    credential_state       VARCHAR(20)  NULL,
    credential_reason      VARCHAR(500) NULL,
    password_changed_at    DATETIME(6)  NULL,
    tokens_not_before      DATETIME(6)  NULL,
    created_at             DATETIME(6)  NULL,
    updated_at             DATETIME(6)  NULL,

    CONSTRAINT pk_users_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_users_aud_revision FOREIGN KEY (rev) REFERENCES auth_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS auth_sessions
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE auth_sessions.login_throttles (
    key_hash             CHAR(64)    NOT NULL,
    consecutive_failures INT         NOT NULL,
    last_failure_at      DATETIME(6) NOT NULL,

    CONSTRAINT pk_login_throttles PRIMARY KEY (key_hash),
    CONSTRAINT chk_login_throttles_key_hash CHECK (REGEXP_LIKE(key_hash, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT chk_login_throttles_failures CHECK (consecutive_failures > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_login_throttles_last_failure ON auth_sessions.login_throttles (last_failure_at);
