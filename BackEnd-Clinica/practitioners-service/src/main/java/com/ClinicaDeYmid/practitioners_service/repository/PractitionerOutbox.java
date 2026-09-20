package com.ClinicaDeYmid.practitioners_service.repository;

import com.ClinicaDeYmid.practitioners_service.repository.entity.Practitioner;
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
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class PractitionerOutbox {

    public static final String TABLE = "practitioners_outbox.outbox_events";

    private static final String INSERT = "INSERT INTO " + TABLE
            + " (id, aggregatetype, aggregateid, type, payload, created_at) VALUES (?, ?, ?, ?, ?, ?)";
    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .build();

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PractitionerOutbox(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void practitionerChanged(Practitioner practitioner, String change) {
        Instant occurredAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        UUID eventId = UUID.randomUUID();
        PractitionerMessage message = PractitionerMessage.of(practitioner, change, eventId, occurredAt,
                MDC.get("traceId"));
        jdbc.update(INSERT, eventId.toString(), PractitionerMessage.AGGREGATE_TYPE, practitioner.uuid().toString(),
                change, write(message), Timestamp.from(occurredAt));
    }

    private static String write(Object message) {
        try {
            return JSON.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize the practitioner event", ex);
        }
    }
}
