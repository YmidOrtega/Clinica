CREATE TABLE rips_submissions (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                 CHAR(36)     NOT NULL,
    version              BIGINT       NOT NULL,
    invoice_id           BIGINT       NOT NULL,
    sequence             INT          NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    rips                 MEDIUMTEXT   NOT NULL,
    response             MEDIUMTEXT   NULL,
    process_id           BIGINT       NULL,
    cuv                  VARCHAR(96)  NULL,
    filed_at             DATETIME(6)  NULL,
    recovered            BOOLEAN      NOT NULL DEFAULT FALSE,
    attempts             INT          NOT NULL DEFAULT 0,
    last_attempt_at      DATETIME(6)  NULL,
    last_failure         VARCHAR(500) NULL,
    resolved_at          DATETIME(6)  NULL,
    created_at           DATETIME(6)  NOT NULL,
    created_by           VARCHAR(36)  NULL,
    updated_at           DATETIME(6)  NOT NULL,
    pending_invoice_id   BIGINT AS (CASE WHEN status = 'PENDING' THEN invoice_id END) STORED,
    validated_invoice_id BIGINT AS (CASE WHEN status = 'VALIDATED' THEN invoice_id END) STORED,

    CONSTRAINT pk_rips_submissions PRIMARY KEY (id),
    CONSTRAINT uk_rips_submissions_uuid UNIQUE (uuid),
    CONSTRAINT uk_rips_submissions_sequence UNIQUE (invoice_id, sequence),
    CONSTRAINT uk_rips_submissions_pending UNIQUE (pending_invoice_id),
    CONSTRAINT uk_rips_submissions_validated UNIQUE (validated_invoice_id),
    CONSTRAINT fk_rips_submissions_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT chk_rips_submissions_status CHECK (status IN ('PENDING', 'VALIDATED', 'REJECTED')),
    CONSTRAINT chk_rips_submissions_cuv
        CHECK ((status = 'VALIDATED') = (cuv IS NOT NULL)
            AND (cuv IS NULL OR REGEXP_LIKE(cuv, '^[0-9a-f]{96}$', 'c'))),
    CONSTRAINT chk_rips_submissions_resolved CHECK ((status = 'PENDING') = (resolved_at IS NULL)),
    CONSTRAINT chk_rips_submissions_counts CHECK (sequence >= 1 AND attempts >= 0),
    INDEX idx_rips_submissions_pending (status, last_attempt_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE rips_submission_findings (
    submission_id BIGINT        NOT NULL,
    position      INT           NOT NULL,
    kind          VARCHAR(30)   NOT NULL,
    code          VARCHAR(20)   NOT NULL,
    description   VARCHAR(2000) NULL,
    observations  VARCHAR(2000) NULL,
    source_path   VARCHAR(500)  NULL,
    source        VARCHAR(60)   NULL,

    CONSTRAINT pk_rips_submission_findings PRIMARY KEY (submission_id, position),
    CONSTRAINT fk_rips_submission_findings_submission FOREIGN KEY (submission_id) REFERENCES rips_submissions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
