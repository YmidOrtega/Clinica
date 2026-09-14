package com.ClinicaDeYmid.clinical_history_service.infrastructure.audit;

import com.ClinicaDeYmid.clinical_history_service.application.access.AccessAudit;
import com.ClinicaDeYmid.clinical_history_service.application.access.AccessEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.UUID;

@Component
class JdbcAccessAuditOutbox implements AccessAudit {

    static final String TABLE = "clinical_outbox.outbox_events";
    static final String AGGREGATE_TYPE = "clinical.access-audit";

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final JdbcTemplate jdbc;

    JdbcAccessAuditOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AccessEvent event) {
        UUID eventId = UUID.randomUUID();
        AccessAuditMessage message = AccessAuditMessage.of(event, eventId, MDC.get("traceId"));
        jdbc.update("INSERT INTO " + TABLE + " (id, aggregatetype, aggregateid, type, payload, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                eventId.toString(), AGGREGATE_TYPE, event.patientUuid().toString(), message.type(), write(message),
                Timestamp.from(event.occurredAt()));
    }

    static String write(AccessAuditMessage message) {
        try {
            return JSON.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize access audit event " + message.eventId(), ex);
        }
    }
}
