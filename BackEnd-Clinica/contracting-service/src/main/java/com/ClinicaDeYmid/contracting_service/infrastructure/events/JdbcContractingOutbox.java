package com.ClinicaDeYmid.contracting_service.infrastructure.events;

import com.ClinicaDeYmid.contracting_service.application.ContractingEventOutbox;
import com.ClinicaDeYmid.contracting_service.domain.Capitation;
import com.ClinicaDeYmid.contracting_service.domain.Contract;
import com.ClinicaDeYmid.contracting_service.domain.Contracts;
import com.ClinicaDeYmid.contracting_service.domain.FundingAgreement;
import com.ClinicaDeYmid.contracting_service.domain.TariffManualVersion;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
class JdbcContractingOutbox implements ContractingEventOutbox {

    static final String TABLE = "contracting_outbox.outbox_events";

    private static final String INSERT = "INSERT INTO " + TABLE
            + " (id, aggregatetype, aggregateid, type, payload, created_at) VALUES (?, ?, ?, ?, ?, ?)";
    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .build();

    private final JdbcTemplate jdbc;
    private final Contracts contracts;
    private final Capitation capitation;
    private final Clock clock;

    JdbcContractingOutbox(JdbcTemplate jdbc, Contracts contracts, Capitation capitation, Clock clock) {
        this.jdbc = jdbc;
        this.contracts = contracts;
        this.capitation = capitation;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void contractChanged(Contract contract, String change) {
        Instant occurredAt = now();
        FundingAgreement funding = capitation.agreementInForce(contract.uuid(), LocalDate.now(clock)).orElse(null);
        ContractMessage message = ContractMessage.of(contract, change,
                contracts.exceptionsOf(contract.uuid()), contracts.packagesOf(contract.uuid()), funding,
                UUID.randomUUID(), occurredAt, MDC.get("traceId"));
        insert(message.eventId(), ContractMessage.AGGREGATE_TYPE, contract.uuid(), change, message, occurredAt);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void tariffVersionChanged(TariffManualVersion version, String change) {
        Instant occurredAt = now();
        TariffVersionMessage message = TariffVersionMessage.of(version, change, UUID.randomUUID(), occurredAt,
                MDC.get("traceId"));
        insert(message.eventId(), TariffVersionMessage.AGGREGATE_TYPE, version.manual().uuid(), change, message, occurredAt);
    }

    private void insert(UUID eventId, String aggregateType, UUID aggregateId, String type, Object message, Instant occurredAt) {
        jdbc.update(INSERT, eventId.toString(), aggregateType, aggregateId.toString(), type, write(message),
                Timestamp.from(occurredAt));
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static String write(Object message) {
        try {
            return JSON.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize the contracting event", ex);
        }
    }
}
