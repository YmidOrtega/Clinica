package com.ClinicaDeYmid.clinical_history_service.infrastructure.events;

import com.ClinicaDeYmid.clinical_history_service.application.integrity.ClinicalEventPublisher;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.fasterxml.jackson.annotation.JsonInclude;
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
class JdbcClinicalEventOutbox implements ClinicalEventPublisher {

    static final String AGGREGATE_TYPE = "clinical.encounters";

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .build();

    private final JdbcTemplate jdbc;

    JdbcClinicalEventOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(LedgerEntry entry, ChainLink link) {
        UUID eventId = UUID.randomUUID();
        EncounterEventMessage message = EncounterEventMessage.of(entry, link, eventId, MDC.get("traceId"));
        jdbc.update("INSERT INTO clinical_outbox.outbox_events (id, aggregatetype, aggregateid, type, payload, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                eventId.toString(), AGGREGATE_TYPE, entry.patientUuid().toString(), message.type(), write(message), Timestamp.from(link.sealedAt()));
    }

    static String write(EncounterEventMessage message) {
        try {
            return JSON.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize clinical event " + message.eventId(), ex);
        }
    }
}
