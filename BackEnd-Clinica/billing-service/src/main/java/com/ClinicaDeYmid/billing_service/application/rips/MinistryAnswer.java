package com.ClinicaDeYmid.billing_service.application.rips;

import com.ClinicaDeYmid.billing_service.domain.MinistryFinding;
import com.ClinicaDeYmid.billing_service.domain.RipsSubmission;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

public record MinistryAnswer(boolean accepted, Long processId, String invoiceNumber, String cuv, Instant filedAt,
                             List<MinistryFinding> findings, String raw) {

    public MinistryAnswer {
        findings = List.copyOf(findings);
    }

    public boolean validated() {
        return accepted && cuv != null && RipsSubmission.CUV.matcher(cuv).matches();
    }

    public Optional<String> cuvOfAnEarlierValidation() {
        for (MinistryFinding finding : findings) {
            for (String text : new String[]{finding.description(), finding.observations()}) {
                if (text != null) {
                    Matcher matcher = RipsSubmission.CUV.matcher(text);
                    if (matcher.find()) {
                        return Optional.of(matcher.group());
                    }
                }
            }
        }
        return Optional.empty();
    }
}
