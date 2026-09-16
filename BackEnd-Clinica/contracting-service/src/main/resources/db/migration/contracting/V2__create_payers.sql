CREATE TABLE payers (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                   CHAR(36)     NOT NULL,
    version                BIGINT       NOT NULL,
    social_reason          VARCHAR(200) NOT NULL,
    nit                    VARCHAR(10)  NOT NULL,
    nit_verification_digit INT          NOT NULL,
    type                   VARCHAR(30)  NOT NULL,
    adres_code             VARCHAR(20)  NULL,
    address                VARCHAR(255) NOT NULL,
    phone                  VARCHAR(16)  NOT NULL,
    billing_email          VARCHAR(150) NULL,
    status                 VARCHAR(20)  NOT NULL,
    status_reason          VARCHAR(500) NULL,
    status_changed_at      DATETIME(6)  NULL,
    created_at             DATETIME(6)  NOT NULL,
    created_by             VARCHAR(36)  NULL,
    updated_at             DATETIME(6)  NOT NULL,
    updated_by             VARCHAR(36)  NULL,

    CONSTRAINT pk_payers PRIMARY KEY (id),
    CONSTRAINT uk_payers_uuid UNIQUE (uuid),
    CONSTRAINT uk_payers_nit UNIQUE (nit),

    CONSTRAINT chk_payers_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_payers_version CHECK (version >= 0),
    CONSTRAINT chk_payers_social_reason CHECK (CHAR_LENGTH(TRIM(social_reason)) >= 3),
    CONSTRAINT chk_payers_nit CHECK (REGEXP_LIKE(nit, '^[0-9]{9,10}$', 'c')),
    CONSTRAINT chk_payers_nit_verification_digit CHECK (nit_verification_digit BETWEEN 0 AND 9),
    CONSTRAINT chk_payers_type
        CHECK (type IN ('EPS', 'IPS', 'ARL', 'PREPAID_MEDICINE', 'COMPLEMENTARY_PLAN', 'HEALTH_POLICY',
                        'STUDENT_POLICY', 'SPECIAL_REGIME', 'OTHER')),
    CONSTRAINT chk_payers_phone CHECK (REGEXP_LIKE(phone, '^\\+?[0-9]{7,15}$', 'c')),
    CONSTRAINT chk_payers_billing_email
        CHECK (billing_email IS NULL
            OR REGEXP_LIKE(billing_email, '^[a-z0-9.!#$%&''*+/=?^_`{|}~-]{1,64}@[a-z0-9-]+([.][a-z0-9-]+)+$', 'c')),
    CONSTRAINT chk_payers_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
    CONSTRAINT chk_payers_status_details
        CHECK ((status = 'ACTIVE' AND status_reason IS NULL AND status_changed_at IS NULL)
            OR (status IN ('SUSPENDED', 'DEACTIVATED')
                    AND CHAR_LENGTH(status_reason) >= 10 AND status_changed_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_payers_social_reason ON payers (social_reason);
CREATE INDEX idx_payers_status ON payers (status);

CREATE TABLE contracting_history.payers_aud (
    id                     BIGINT       NOT NULL,
    rev                    BIGINT       NOT NULL,
    revtype                TINYINT      NOT NULL,
    uuid                   CHAR(36)     NULL,
    social_reason          VARCHAR(200) NULL,
    nit                    VARCHAR(10)  NULL,
    nit_verification_digit INT          NULL,
    type                   VARCHAR(30)  NULL,
    adres_code             VARCHAR(20)  NULL,
    address                VARCHAR(255) NULL,
    phone                  VARCHAR(16)  NULL,
    billing_email          VARCHAR(150) NULL,
    status                 VARCHAR(20)  NULL,
    status_reason          VARCHAR(500) NULL,
    status_changed_at      DATETIME(6)  NULL,
    created_at             DATETIME(6)  NULL,
    created_by             VARCHAR(36)  NULL,
    updated_at             DATETIME(6)  NULL,
    updated_by             VARCHAR(36)  NULL,

    CONSTRAINT pk_payers_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_payers_aud_revision FOREIGN KEY (rev) REFERENCES contracting_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
