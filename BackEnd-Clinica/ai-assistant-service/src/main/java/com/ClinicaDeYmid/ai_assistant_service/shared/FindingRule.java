package com.ClinicaDeYmid.ai_assistant_service.shared;

public enum FindingRule {

    DIAN_REJECTED(Severity.HIGH, false),
    DIAN_UNCONFIRMED(Severity.MEDIUM, false),
    CREDIT_NOTE_REJECTED(Severity.HIGH, false),
    RIPS_PENDING(Severity.MEDIUM, false),
    COPAY_SHORTFALL(Severity.MEDIUM, false),
    BILLED_WITHOUT_CONTRACT(Severity.LOW, false),
    FILING_DUE_SOON(Severity.MEDIUM, true),
    FILING_OVERDUE(Severity.HIGH, true),
    OBJECTION_DUE_SOON(Severity.MEDIUM, true),
    OBJECTION_OVERDUE(Severity.HIGH, true);

    public enum Severity {
        HIGH,
        MEDIUM,
        LOW
    }

    private final Severity severity;
    private final boolean raisedByBilling;

    FindingRule(Severity severity, boolean raisedByBilling) {
        this.severity = severity;
        this.raisedByBilling = raisedByBilling;
    }

    public Severity severity() {
        return severity;
    }

    public boolean raisedByBilling() {
        return raisedByBilling;
    }
}
