package com.ClinicaDeYmid.ai_assistant_service.messaging;

import com.ClinicaDeYmid.ai_assistant_service.service.DeadlineAlert;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

final class DeadlineAlertMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private DeadlineAlertMapper() {
    }

    static DeadlineAlert filing(String payload) {
        try {
            JsonNode alert = JSON.readTree(payload);
            return new DeadlineAlert(DeadlineAlert.Kind.FILING, text(alert, "state"),
                    UUID.fromString(text(alert, "invoiceUuid")), text(alert, "invoiceNumber"), null, null, null,
                    text(alert, "payerName"), alert.path("payableTotal").decimalValue(),
                    LocalDate.parse(text(alert, "deadline")), alert.path("remainingBusinessDays").asInt());
        } catch (MalformedInvoiceEventException malformed) {
            throw malformed;
        } catch (Exception unreadable) {
            throw new MalformedInvoiceEventException("The filing alert cannot be read: " + unreadable.getMessage(),
                    unreadable);
        }
    }

    static DeadlineAlert objection(String payload) {
        try {
            JsonNode alert = JSON.readTree(payload);
            return new DeadlineAlert(DeadlineAlert.Kind.OBJECTION, text(alert, "state"),
                    UUID.fromString(text(alert, "invoiceUuid")), text(alert, "invoiceNumber"),
                    UUID.fromString(text(alert, "objectionUuid")), text(alert, "kind"), text(alert, "payerRecord"),
                    text(alert, "payerName"), alert.path("claimedAmount").decimalValue(),
                    LocalDate.parse(text(alert, "responseDeadline")), alert.path("remainingBusinessDays").asInt());
        } catch (MalformedInvoiceEventException malformed) {
            throw malformed;
        } catch (Exception unreadable) {
            throw new MalformedInvoiceEventException("The objection alert cannot be read: " + unreadable.getMessage(),
                    unreadable);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new MalformedInvoiceEventException("The alert has no " + field, null);
        }
        return value.asText();
    }
}
