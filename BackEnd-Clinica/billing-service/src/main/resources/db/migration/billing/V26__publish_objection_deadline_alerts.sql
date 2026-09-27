ALTER TABLE billing_outbox.outbox_events
    DROP CONSTRAINT chk_outbox_events_aggregatetype,
    DROP CONSTRAINT chk_outbox_events_type,
    ADD CONSTRAINT chk_outbox_events_aggregatetype
        CHECK (aggregatetype IN ('billing.filing-deadlines', 'billing.claim-objections')),
    ADD CONSTRAINT chk_outbox_events_type CHECK (type IN ('FilingDeadlineApproaching', 'FilingDeadlineMissed',
        'ObjectionResponseDueSoon', 'ObjectionResponseMissed'));

CREATE TABLE objection_alerts (
    objection_id BIGINT      NOT NULL,
    state        VARCHAR(20) NOT NULL,
    alerted_on   DATE        NOT NULL,
    event_id     CHAR(36)    NOT NULL,

    CONSTRAINT pk_objection_alerts PRIMARY KEY (objection_id, state),
    CONSTRAINT fk_objection_alerts_objection FOREIGN KEY (objection_id) REFERENCES payer_objections (id),
    CONSTRAINT chk_objection_alerts_state CHECK (state IN ('DUE_SOON', 'OVERDUE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
