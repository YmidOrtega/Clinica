package com.ClinicaDeYmid.ai_assistant_service.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DeadlineAlert(Kind kind, String state, UUID invoiceUuid, String invoiceNumber, UUID objectionUuid,
                            String objectionKind, String payerRecord, String payerName, BigDecimal amount,
                            LocalDate deadline, int remainingBusinessDays) {

    public enum Kind {
        FILING,
        OBJECTION
    }

    public boolean overdue() {
        return "OVERDUE".equals(state);
    }
}
