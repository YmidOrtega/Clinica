CREATE TABLE assistant.findings (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            UUID         NOT NULL,
    version         BIGINT       NOT NULL,
    invoice_uuid    UUID         NOT NULL,
    invoice_number  VARCHAR(24)  NOT NULL,
    rule            VARCHAR(40)  NOT NULL,
    subject         VARCHAR(40)  NOT NULL,
    severity        VARCHAR(10)  NOT NULL,
    detail          VARCHAR(500) NOT NULL,
    due_on          DATE         NULL,
    status          VARCHAR(10)  NOT NULL,
    detected_at     TIMESTAMPTZ  NOT NULL,
    resolved_at     TIMESTAMPTZ  NULL,
    CONSTRAINT uk_findings_uuid UNIQUE (uuid),
    CONSTRAINT chk_findings_rule CHECK (rule IN ('DIAN_REJECTED', 'DIAN_UNCONFIRMED', 'CREDIT_NOTE_REJECTED',
        'RIPS_PENDING', 'COPAY_SHORTFALL', 'BILLED_WITHOUT_CONTRACT', 'FILING_DUE_SOON', 'FILING_OVERDUE',
        'OBJECTION_DUE_SOON', 'OBJECTION_OVERDUE')),
    CONSTRAINT chk_findings_severity CHECK (severity IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_findings_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT chk_findings_resolved CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);

CREATE UNIQUE INDEX uk_findings_open ON assistant.findings (invoice_uuid, rule, subject) WHERE status = 'OPEN';
CREATE INDEX idx_findings_tray ON assistant.findings (status, severity, due_on, detected_at);
CREATE INDEX idx_findings_invoice ON assistant.findings (invoice_uuid);
