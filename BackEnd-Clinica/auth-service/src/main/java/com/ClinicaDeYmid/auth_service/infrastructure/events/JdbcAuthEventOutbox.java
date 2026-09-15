package com.ClinicaDeYmid.auth_service.infrastructure.events;

import com.ClinicaDeYmid.auth_service.application.audit.SecurityAuditLog;
import com.ClinicaDeYmid.auth_service.application.audit.SecurityEvent;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
public class JdbcAuthEventOutbox implements SecurityAuditLog {

    static final String TABLE = "auth_outbox.outbox_events";

    private static final String INSERT = "INSERT INTO " + TABLE + " (id, aggregatetype, aggregateid, type, payload, created_at) VALUES (?, ?, ?, ?, ?, ?)";
    private static final int MAX_USER_AGENT_LENGTH = 255;
    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .build();

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcAuthEventOutbox(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appendUserEvents(User user, List<UserEvent> events) {
        Instant occurredAt = now();
        String traceId = MDC.get("traceId");
        SecurityAuditMessage.ClientView client = client();
        for (UserEvent event : events) {
            UserEventMessage.of(event, user, UUID.randomUUID(), occurredAt, traceId)
                    .ifPresent(message -> insert(message.eventId(), UserEventMessage.AGGREGATE_TYPE, message.userUuid(), message.type(), message,
                            occurredAt));
            SecurityAuditMessage audit = SecurityAuditMessage.ofUserEvent(event, user, UUID.randomUUID(), occurredAt, traceId, client);
            insert(audit.eventId(), SecurityAuditMessage.AGGREGATE_TYPE, audit.aggregateId(), audit.type(), audit, occurredAt);
        }
    }

    @Override
    @Transactional
    public void record(SecurityEvent event) {
        Instant occurredAt = now();
        SecurityAuditMessage audit = SecurityAuditMessage.ofSecurityEvent(event, UUID.randomUUID(), occurredAt, MDC.get("traceId"), client());
        insert(audit.eventId(), SecurityAuditMessage.AGGREGATE_TYPE, audit.aggregateId(), audit.type(), audit, occurredAt);
    }

    static String write(Object message) {
        try {
            return JSON.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize the auth event", ex);
        }
    }

    private void insert(UUID eventId, String aggregateType, UUID aggregateId, String type, Object message, Instant occurredAt) {
        jdbc.update(INSERT, eventId.toString(), aggregateType, aggregateId.toString(), type, write(message), Timestamp.from(occurredAt));
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static SecurityAuditMessage.ClientView client() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT_LENGTH) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT_LENGTH);
        }
        return new SecurityAuditMessage.ClientView(request.getRemoteAddr(), userAgent);
    }
}
