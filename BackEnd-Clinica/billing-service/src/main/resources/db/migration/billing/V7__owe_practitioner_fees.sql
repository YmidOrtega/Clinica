CREATE TABLE practitioner_fees (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)      NOT NULL,
    version           BIGINT        NOT NULL,
    sale_id           BIGINT        NOT NULL,
    sale_line_uuid    CHAR(36)      NOT NULL,
    cups_code         VARCHAR(8)    NOT NULL,
    description       VARCHAR(300)  NOT NULL,
    role              VARCHAR(20)   NOT NULL,
    practitioner_uuid CHAR(36)      NOT NULL,
    practitioner_name VARCHAR(200)  NOT NULL,
    performed_on      DATE          NOT NULL,
    agreement_uuid    CHAR(36)      NULL,
    amount            DECIMAL(14,2) NULL,
    status            VARCHAR(20)   NOT NULL,
    status_reason     VARCHAR(500)  NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,

    CONSTRAINT pk_practitioner_fees PRIMARY KEY (id),
    CONSTRAINT uk_practitioner_fees_uuid UNIQUE (uuid),
    CONSTRAINT uk_practitioner_fees_line_role UNIQUE (sale_line_uuid, role),
    CONSTRAINT fk_practitioner_fees_sale FOREIGN KEY (sale_id) REFERENCES sales (id),

    CONSTRAINT chk_practitioner_fees_role CHECK (role IN ('SURGEON', 'ANESTHESIOLOGIST', 'ASSISTANT')),
    CONSTRAINT chk_practitioner_fees_status CHECK (status IN ('PAYABLE', 'UNAGREED', 'VOIDED')),
    CONSTRAINT chk_practitioner_fees_amount
        CHECK ((status = 'PAYABLE' AND amount > 0 AND agreement_uuid IS NOT NULL)
            OR (status = 'UNAGREED' AND amount IS NULL AND CHAR_LENGTH(TRIM(status_reason)) >= 1)
            OR (status = 'VOIDED' AND CHAR_LENGTH(TRIM(status_reason)) >= 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_practitioner_fees_practitioner ON practitioner_fees (practitioner_uuid, status, performed_on);

CREATE TABLE billing_history.practitioner_fees_aud (
    id                BIGINT        NOT NULL,
    rev               BIGINT        NOT NULL,
    revtype           TINYINT       NOT NULL,
    uuid              CHAR(36)      NULL,
    sale_id           BIGINT        NULL,
    sale_line_uuid    CHAR(36)      NULL,
    cups_code         VARCHAR(8)    NULL,
    description       VARCHAR(300)  NULL,
    role              VARCHAR(20)   NULL,
    practitioner_uuid CHAR(36)      NULL,
    practitioner_name VARCHAR(200)  NULL,
    performed_on      DATE          NULL,
    agreement_uuid    CHAR(36)      NULL,
    amount            DECIMAL(14,2) NULL,
    status            VARCHAR(20)   NULL,
    status_reason     VARCHAR(500)  NULL,
    created_at        DATETIME(6)   NULL,
    updated_at        DATETIME(6)   NULL,

    CONSTRAINT pk_practitioner_fees_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_practitioner_fees_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
