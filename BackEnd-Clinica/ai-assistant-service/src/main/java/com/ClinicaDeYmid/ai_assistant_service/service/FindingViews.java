package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.repository.entity.Finding;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FindingViews {

    private FindingViews() {
    }

    public record FindingView(UUID uuid, UUID invoiceUuid, String invoiceNumber, String rule, String severity,
                              String detail, LocalDate dueOn, String status, Instant detectedAt, Instant resolvedAt) {

        static FindingView of(Finding finding) {
            return new FindingView(finding.uuid(), finding.invoiceUuid(), finding.invoiceNumber(),
                    finding.rule().name(), finding.severity().name(), finding.detail(), finding.dueOn(),
                    finding.status().name(), finding.detectedAt(), finding.resolvedAt());
        }
    }

    public record RuleCount(String rule, String severity, long open) {
    }

    public record PageOf<T>(List<T> content, int page, int size, long totalElements) {
    }

    public record Summary(long open, long high, List<RuleCount> byRule) {
    }
}
