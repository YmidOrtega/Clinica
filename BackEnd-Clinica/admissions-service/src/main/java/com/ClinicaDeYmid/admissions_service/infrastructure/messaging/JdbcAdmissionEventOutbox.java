package com.ClinicaDeYmid.admissions_service.infrastructure.messaging;

import com.ClinicaDeYmid.admissions_service.application.AdmissionEventOutbox;
import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionEvent;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
class JdbcAdmissionEventOutbox implements AdmissionEventOutbox {

    static final String TABLE = "admissions_outbox.outbox_events";
    static final String AGGREGATE_TYPE = "admissions.events";

    private static final String INSERT = "INSERT INTO " + TABLE
            + " (id, aggregatetype, aggregateid, type, payload, created_at) VALUES (?, ?, ?, ?, ?::jsonb, ?)";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcAdmissionEventOutbox(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Admission admission, List<AdmissionEvent> events) {
        Instant occurredAt = Instant.now(clock);
        String traceId = MDC.get("traceId");
        for (AdmissionEvent event : events) {
            UUID eventId = UUID.randomUUID();
            AdmissionEventMessage message = AdmissionEventMessage.of(event, admission, eventId, occurredAt, traceId);
            jdbc.update(INSERT, eventId, AGGREGATE_TYPE, admission.uuid(), message.type(),
                    AdmissionEventJson.write(message), Timestamp.from(occurredAt));
        }
    }
}
