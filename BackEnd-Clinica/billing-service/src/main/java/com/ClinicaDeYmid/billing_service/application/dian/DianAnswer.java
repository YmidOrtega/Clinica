package com.ClinicaDeYmid.billing_service.application.dian;

import java.util.List;

public record DianAnswer(Verdict verdict, String statusCode, String statusDescription, List<String> errors,
                         String applicationResponse) {

    public enum Verdict {
        PROCESSING,
        ACCEPTED,
        REJECTED
    }

    public DianAnswer {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
