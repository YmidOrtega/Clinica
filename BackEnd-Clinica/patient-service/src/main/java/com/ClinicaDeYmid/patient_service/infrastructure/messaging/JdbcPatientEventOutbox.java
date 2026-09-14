package com.ClinicaDeYmid.patient_service.infrastructure.messaging;

import com.ClinicaDeYmid.patient_service.application.PatientEventOutbox;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientEvent;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatient;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatientEvent;
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
class JdbcPatientEventOutbox implements PatientEventOutbox {

    static final String TABLE = "patient_outbox.outbox_events";
    static final String AGGREGATE_TYPE = "patient";

    private static final String INSERT = "INSERT INTO " + TABLE
            + " (id, aggregatetype, aggregateid, type, payload, created_at) VALUES (?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcPatientEventOutbox(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Patient patient, List<PatientEvent> events) {
        Instant occurredAt = Instant.now(clock);
        String traceId = MDC.get("traceId");
        for (PatientEvent event : events) {
            UUID eventId = UUID.randomUUID();
            PatientEventMessage message = PatientEventMessage.of(event, patient, eventId, occurredAt, traceId);
            insert(eventId, patient.uuid(), message.type(), PatientEventJson.write(message), occurredAt);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendUnidentified(UnidentifiedPatient patient, List<UnidentifiedPatientEvent> events) {
        Instant occurredAt = Instant.now(clock);
        String traceId = MDC.get("traceId");
        for (UnidentifiedPatientEvent event : events) {
            UUID eventId = UUID.randomUUID();
            UnidentifiedPatientEventMessage message = UnidentifiedPatientEventMessage.of(event, patient, eventId, occurredAt, traceId);
            insert(eventId, patient.uuid(), message.type(), PatientEventJson.write(message), occurredAt);
        }
    }

    private void insert(UUID eventId, UUID aggregateId, String type, String payload, Instant occurredAt) {
        jdbc.update(INSERT, eventId.toString(), AGGREGATE_TYPE, aggregateId.toString(), type, payload, Timestamp.from(occurredAt));
    }
}
