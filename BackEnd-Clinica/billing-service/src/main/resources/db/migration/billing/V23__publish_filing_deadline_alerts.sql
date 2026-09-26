CREATE TABLE billing_outbox.outbox_events (
    id            CHAR(36)    NOT NULL,
    aggregatetype VARCHAR(50) NOT NULL,
    aggregateid   CHAR(36)    NOT NULL,
    type          VARCHAR(80) NOT NULL,
    payload       JSON        NOT NULL,
    created_at    DATETIME(6) NOT NULL,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT chk_outbox_events_aggregatetype CHECK (aggregatetype IN ('billing.filing-deadlines')),
    CONSTRAINT chk_outbox_events_type CHECK (type IN ('FilingDeadlineApproaching', 'FilingDeadlineMissed'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_outbox_events_created_at ON billing_outbox.outbox_events (created_at);

CREATE TABLE filing_alerts (
    invoice_id BIGINT      NOT NULL,
    state      VARCHAR(20) NOT NULL,
    alerted_on DATE        NOT NULL,
    event_id   CHAR(36)    NOT NULL,

    CONSTRAINT pk_filing_alerts PRIMARY KEY (invoice_id, state),
    CONSTRAINT fk_filing_alerts_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT chk_filing_alerts_state CHECK (state IN ('DUE_SOON', 'OVERDUE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
