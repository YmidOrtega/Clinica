CREATE TABLE episode_accounts (
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                       CHAR(36)     NOT NULL,
    version                    BIGINT       NOT NULL,
    admission_uuid             CHAR(36)     NOT NULL,
    admission_number           VARCHAR(15)  NOT NULL,
    admission_version          BIGINT       NOT NULL,
    patient_uuid               CHAR(36)     NOT NULL,
    admission_kind             VARCHAR(20)  NOT NULL,
    admission_status           VARCHAR(20)  NOT NULL,
    configuration_service_uuid CHAR(36)     NOT NULL,
    opened_at                  DATETIME(6)  NOT NULL,
    status                     VARCHAR(20)  NOT NULL,
    discharge                  VARCHAR(20)  NULL,
    status_reason              VARCHAR(500) NULL,
    status_changed_at          DATETIME(6)  NULL,
    created_at                 DATETIME(6)  NOT NULL,
    updated_at                 DATETIME(6)  NOT NULL,

    CONSTRAINT pk_episode_accounts PRIMARY KEY (id),
    CONSTRAINT uk_episode_accounts_uuid UNIQUE (uuid),
    CONSTRAINT uk_episode_accounts_admission UNIQUE (admission_uuid),
    CONSTRAINT uk_episode_accounts_admission_number UNIQUE (admission_number),

    CONSTRAINT chk_episode_accounts_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_episode_accounts_admission_uuid
        CHECK (REGEXP_LIKE(admission_uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_episode_accounts_patient_uuid
        CHECK (REGEXP_LIKE(patient_uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_episode_accounts_version CHECK (version >= 0 AND admission_version >= 0),
    CONSTRAINT chk_episode_accounts_admission_number
        CHECK (REGEXP_LIKE(admission_number, '^ADM-[0-9]{4}-[0-9]{6}$', 'c')),
    CONSTRAINT chk_episode_accounts_kind CHECK (admission_kind IN ('EMERGENCY', 'INPATIENT', 'OUTPATIENT')),
    CONSTRAINT chk_episode_accounts_admission_status
        CHECK (admission_status IN ('REGISTERED', 'ACTIVE', 'DISCHARGED', 'CANCELLED')),
    CONSTRAINT chk_episode_accounts_status CHECK (status IN ('OPEN', 'FROZEN', 'VOIDED')),
    CONSTRAINT chk_episode_accounts_discharge
        CHECK (discharge IS NULL OR discharge IN ('MEDICAL', 'VOLUNTARY', 'REFERRAL', 'ESCAPE', 'DEATH')),
    CONSTRAINT chk_episode_accounts_status_details
        CHECK ((status = 'OPEN' AND discharge IS NULL AND status_reason IS NULL AND status_changed_at IS NULL
                   AND admission_status IN ('REGISTERED', 'ACTIVE'))
            OR (status = 'FROZEN' AND status_reason IS NULL AND status_changed_at IS NOT NULL
                   AND admission_status = 'DISCHARGED')
            OR (status = 'VOIDED' AND discharge IS NULL AND CHAR_LENGTH(TRIM(status_reason)) >= 1
                   AND status_changed_at IS NOT NULL AND admission_status = 'CANCELLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_episode_accounts_status ON episode_accounts (status, status_changed_at);
CREATE INDEX idx_episode_accounts_patient ON episode_accounts (patient_uuid);

CREATE TABLE billing_history.episode_accounts_aud (
    id                         BIGINT       NOT NULL,
    rev                        BIGINT       NOT NULL,
    revtype                    TINYINT      NOT NULL,
    uuid                       CHAR(36)     NULL,
    admission_uuid             CHAR(36)     NULL,
    admission_number           VARCHAR(15)  NULL,
    admission_version          BIGINT       NULL,
    patient_uuid               CHAR(36)     NULL,
    admission_kind             VARCHAR(20)  NULL,
    admission_status           VARCHAR(20)  NULL,
    configuration_service_uuid CHAR(36)     NULL,
    opened_at                  DATETIME(6)  NULL,
    status                     VARCHAR(20)  NULL,
    discharge                  VARCHAR(20)  NULL,
    status_reason              VARCHAR(500) NULL,
    status_changed_at          DATETIME(6)  NULL,
    created_at                 DATETIME(6)  NULL,
    updated_at                 DATETIME(6)  NULL,

    CONSTRAINT pk_episode_accounts_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_episode_accounts_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
