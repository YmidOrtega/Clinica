package com.ClinicaDeYmid.billing_service.infrastructure.events;

import com.ClinicaDeYmid.billing_service.application.filing.FilingAlertOutbox;
import com.ClinicaDeYmid.billing_service.domain.BusinessDeadline;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
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

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
class JdbcFilingAlertOutbox implements FilingAlertOutbox {

    static final String TABLE = "billing_outbox.outbox_events";

    private static final String ALREADY_ALERTED = """
            SELECT COUNT(*) FROM filing_alerts a JOIN invoices i ON i.id = a.invoice_id
            WHERE i.uuid = ? AND a.state = ?""";
    private static final String REMEMBER = """
            INSERT INTO filing_alerts (invoice_id, state, alerted_on, event_id)
            SELECT i.id, ?, ?, ? FROM invoices i WHERE i.uuid = ?""";
    private static final String INSERT = "INSERT INTO " + TABLE
            + " (id, aggregatetype, aggregateid, type, payload, created_at) VALUES (?, ?, ?, ?, ?, ?)";
    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .build();

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcFilingAlertOutbox(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean alertOnce(Invoice invoice, BusinessDeadline deadline, String cuv) {
        String state = deadline.state().name();
        Integer earlier = jdbc.queryForObject(ALREADY_ALERTED, Integer.class, invoice.uuid().toString(), state);
        if (earlier != null && earlier > 0) {
            return false;
        }
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        jdbc.update(REMEMBER, state, Date.valueOf(LocalDate.now(clock)), eventId.toString(), invoice.uuid().toString());
        FilingDeadlineMessage message = FilingDeadlineMessage.of(invoice, deadline, cuv, eventId, occurredAt,
                MDC.get("traceId"));
        jdbc.update(INSERT, eventId.toString(), FilingDeadlineMessage.AGGREGATE_TYPE, invoice.uuid().toString(),
                message.type(), write(message), Timestamp.from(occurredAt));
        return true;
    }

    private static String write(Object message) {
        try {
            return JSON.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize the billing event", ex);
        }
    }
}
