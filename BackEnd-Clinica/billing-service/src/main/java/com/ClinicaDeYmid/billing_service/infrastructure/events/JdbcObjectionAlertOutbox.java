package com.ClinicaDeYmid.billing_service.infrastructure.events;

import com.ClinicaDeYmid.billing_service.application.objection.ObjectionAlertOutbox;
import com.ClinicaDeYmid.billing_service.domain.BusinessDeadline;
import com.ClinicaDeYmid.billing_service.domain.PayerObjection;
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
class JdbcObjectionAlertOutbox implements ObjectionAlertOutbox {

    private static final String ALREADY_ALERTED = """
            SELECT COUNT(*) FROM objection_alerts a JOIN payer_objections o ON o.id = a.objection_id
            WHERE o.uuid = ? AND a.state = ?""";
    private static final String REMEMBER = """
            INSERT INTO objection_alerts (objection_id, state, alerted_on, event_id)
            SELECT o.id, ?, ?, ? FROM payer_objections o WHERE o.uuid = ?""";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcObjectionAlertOutbox(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean alertOnce(PayerObjection objection, BusinessDeadline responseDue) {
        String state = responseDue.state().name();
        Integer earlier = jdbc.queryForObject(ALREADY_ALERTED, Integer.class, objection.uuid().toString(), state);
        if (earlier != null && earlier > 0) {
            return false;
        }
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        jdbc.update(REMEMBER, state, Date.valueOf(LocalDate.now(clock)), eventId.toString(),
                objection.uuid().toString());
        ObjectionDeadlineMessage message = ObjectionDeadlineMessage.of(objection, responseDue, eventId, occurredAt,
                MDC.get("traceId"));
        jdbc.update(JdbcFilingAlertOutbox.INSERT, eventId.toString(), ObjectionDeadlineMessage.AGGREGATE_TYPE,
                objection.uuid().toString(), message.type(), JdbcFilingAlertOutbox.write(message),
                Timestamp.from(occurredAt));
        return true;
    }
}
