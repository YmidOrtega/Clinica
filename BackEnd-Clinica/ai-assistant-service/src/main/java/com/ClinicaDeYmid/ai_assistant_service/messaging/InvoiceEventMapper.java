package com.ClinicaDeYmid.ai_assistant_service.messaging;

import com.ClinicaDeYmid.ai_assistant_service.service.InvoiceState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

final class InvoiceEventMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private InvoiceEventMapper() {
    }

    static Optional<InvoiceState> toState(String payload) {
        if (payload == null) {
            return Optional.empty();
        }
        try {
            JsonNode event = JSON.readTree(payload);
            JsonNode buyer = required(event, "buyer");
            JsonNode dian = event.path("dian");
            JsonNode rips = event.path("rips");
            JsonNode filing = event.path("filing");
            return Optional.of(new InvoiceState(
                    UUID.fromString(text(event, "invoiceUuid")),
                    text(event, "type"),
                    Instant.parse(text(event, "occurredAt")),
                    text(event, "number"),
                    text(event, "purpose"),
                    text(event, "status"),
                    LocalDate.parse(text(event, "issuedOn")),
                    text(event, "admissionNumber"),
                    text(buyer, "kind"),
                    optional(buyer, "nit"),
                    optional(event, "contractNumber"),
                    optional(event, "uncontractedCare"),
                    amount(event, "payableTotal"),
                    amount(event, "creditedTotal"),
                    amount(event, "balance"),
                    event.hasNonNull("shareShortfall") ? event.get("shareShortfall").decimalValue() : null,
                    optional(dian, "status"),
                    dian.hasNonNull("statusAt") ? Instant.parse(dian.get("statusAt").asText()) : null,
                    optional(rips, "cuv"),
                    optional(filing, "filingNumber"),
                    filing.hasNonNull("filedOn") ? LocalDate.parse(filing.get("filedOn").asText()) : null,
                    payload));
        } catch (MalformedInvoiceEventException malformed) {
            throw malformed;
        } catch (Exception unreadable) {
            throw new MalformedInvoiceEventException("The invoice event cannot be read: " + unreadable.getMessage(),
                    unreadable);
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new MalformedInvoiceEventException("The invoice event has no " + field, null);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        return required(node, field).asText();
    }

    private static String optional(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static BigDecimal amount(JsonNode node, String field) {
        return required(node, field).decimalValue();
    }
}
