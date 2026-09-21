CREATE TABLE admissions_outbox.outbox_events (
    id            UUID         NOT NULL,
    aggregatetype VARCHAR(50)  NOT NULL,
    aggregateid   UUID         NOT NULL,
    type          VARCHAR(80)  NOT NULL,
    payload       JSONB        NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT chk_outbox_events_aggregatetype CHECK (aggregatetype = 'admissions.events'),
    CONSTRAINT chk_outbox_events_type CHECK (type IN ('AdmissionRegistered', 'AdmissionPhaseChanged',
                                                      'AdmissionBedAssigned', 'AdmissionBedReleased',
                                                      'AdmissionCoveragePending', 'AdmissionDischarged',
                                                      'AdmissionCancelled'))
);

CREATE INDEX idx_outbox_events_created_at ON admissions_outbox.outbox_events (created_at);
