package com.ClinicaDeYmid.clinical_history_service.domain.access;

public sealed interface AccessDecision {

    record Granted(AccessBasis basis) implements AccessDecision {
    }

    record Denied() implements AccessDecision {
    }
}
