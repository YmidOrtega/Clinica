CREATE TABLE payer_objections (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    uuid                 CHAR(36)      NOT NULL,
    version              BIGINT        NOT NULL,
    invoice_id           BIGINT        NOT NULL,
    kind                 VARCHAR(20)   NOT NULL,
    payer_record         VARCHAR(60)   NOT NULL,
    notified_on          DATE          NOT NULL,
    formulation_deadline DATE          NOT NULL,
    response_deadline    DATE          NOT NULL,
    claimed_amount       DECIMAL(14,2) NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    response_record      VARCHAR(60)   NULL,
    responded_on         DATE          NULL,
    accepted_amount      DECIMAL(14,2) NULL,
    credit_note_id       BIGINT        NULL,
    decided_on           DATE          NULL,
    upheld_amount        DECIMAL(14,2) NULL,
    created_at           DATETIME(6)   NOT NULL,
    created_by           VARCHAR(36)   NULL,
    updated_at           DATETIME(6)   NOT NULL,
    updated_by           VARCHAR(36)   NULL,
    devolution_invoice_id BIGINT AS (CASE WHEN kind = 'DEVOLUTION' THEN invoice_id END) STORED,

    CONSTRAINT pk_payer_objections PRIMARY KEY (id),
    CONSTRAINT uk_payer_objections_uuid UNIQUE (uuid),
    CONSTRAINT uk_payer_objections_devolution UNIQUE (devolution_invoice_id),
    CONSTRAINT uk_payer_objections_credit_note UNIQUE (credit_note_id),
    CONSTRAINT fk_payer_objections_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_payer_objections_credit_note FOREIGN KEY (credit_note_id) REFERENCES credit_notes (id),
    CONSTRAINT chk_payer_objections_kind CHECK (kind IN ('DEVOLUTION', 'GLOSS')),
    CONSTRAINT chk_payer_objections_status CHECK (status IN ('AWAITING_RESPONSE', 'RESPONDED', 'DECIDED')),
    CONSTRAINT chk_payer_objections_claimed CHECK (claimed_amount > 0),
    CONSTRAINT chk_payer_objections_response
        CHECK ((status = 'AWAITING_RESPONSE') = (responded_on IS NULL)
            AND (responded_on IS NULL) = (accepted_amount IS NULL)
            AND (responded_on IS NULL) = (response_record IS NULL)),
    CONSTRAINT chk_payer_objections_decision
        CHECK ((status = 'DECIDED') = (decided_on IS NOT NULL) AND (decided_on IS NULL) = (upheld_amount IS NULL)),
    CONSTRAINT chk_payer_objections_amounts
        CHECK ((accepted_amount IS NULL OR accepted_amount BETWEEN 0 AND claimed_amount)
            AND (upheld_amount IS NULL OR upheld_amount BETWEEN 0 AND claimed_amount - accepted_amount)),
    INDEX idx_payer_objections_awaiting (status, response_deadline)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE payer_objection_items (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    objection_id          BIGINT        NOT NULL,
    position              INT           NOT NULL,
    invoice_line_position INT           NULL,
    code                  VARCHAR(6)    NOT NULL,
    amount                DECIMAL(14,2) NOT NULL,
    detail                VARCHAR(500)  NULL,
    response_code         VARCHAR(6)    NULL,
    accepted_amount       DECIMAL(14,2) NULL,
    response_detail       VARCHAR(1000) NULL,
    upheld_amount         DECIMAL(14,2) NULL,

    CONSTRAINT pk_payer_objection_items PRIMARY KEY (id),
    CONSTRAINT uk_payer_objection_items_position UNIQUE (objection_id, position),
    CONSTRAINT fk_payer_objection_items_objection FOREIGN KEY (objection_id) REFERENCES payer_objections (id),
    CONSTRAINT fk_payer_objection_items_code FOREIGN KEY (code) REFERENCES objection_codes (code),
    CONSTRAINT fk_payer_objection_items_response FOREIGN KEY (response_code) REFERENCES objection_codes (code),
    CONSTRAINT chk_payer_objection_items_amount CHECK (amount > 0),
    CONSTRAINT chk_payer_objection_items_accepted
        CHECK ((response_code IS NULL) = (accepted_amount IS NULL)
            AND (accepted_amount IS NULL OR accepted_amount BETWEEN 0 AND amount)),
    CONSTRAINT chk_payer_objection_items_upheld
        CHECK (upheld_amount IS NULL OR upheld_amount BETWEEN 0 AND amount - accepted_amount)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE billing_history.payer_objections_aud (
    id                   BIGINT        NOT NULL,
    rev                  BIGINT        NOT NULL,
    revtype              TINYINT       NOT NULL,
    uuid                 CHAR(36)      NULL,
    invoice_id           BIGINT        NULL,
    kind                 VARCHAR(20)   NULL,
    payer_record         VARCHAR(60)   NULL,
    notified_on          DATE          NULL,
    formulation_deadline DATE          NULL,
    response_deadline    DATE          NULL,
    claimed_amount       DECIMAL(14,2) NULL,
    status               VARCHAR(20)   NULL,
    response_record      VARCHAR(60)   NULL,
    responded_on         DATE          NULL,
    accepted_amount      DECIMAL(14,2) NULL,
    credit_note_id       BIGINT        NULL,
    decided_on           DATE          NULL,
    upheld_amount        DECIMAL(14,2) NULL,
    created_at           DATETIME(6)   NULL,
    created_by           VARCHAR(36)   NULL,
    updated_at           DATETIME(6)   NULL,
    updated_by           VARCHAR(36)   NULL,

    CONSTRAINT pk_payer_objections_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_payer_objections_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
