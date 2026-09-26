package com.ClinicaDeYmid.billing_service.domain;

public enum SharedPaymentKind {

    COPAYMENT("COPAGO", "Copago"),
    MODERATING_FEE("CUOTA_MODERADORA", "Cuota moderadora"),
    RECOVERY_FEE("CUOTA_RECUPERACION", "Cuota de recuperación"),
    VOLUNTARY_PLAN("PAGOS_COMPARTIDOS", "Pago compartido de plan voluntario");

    static final String CONTRIBUTORY = "CONTRIBUTORY";

    private final String healthField;
    private final String label;

    SharedPaymentKind(String healthField, String label) {
        this.healthField = healthField;
        this.label = label;
    }

    public static SharedPaymentKind proposedFor(String healthRegime, AdmissionKind admission) {
        if (CONTRIBUTORY.equals(healthRegime) && admission == AdmissionKind.OUTPATIENT) {
            return MODERATING_FEE;
        }
        return COPAYMENT;
    }

    public String healthField() {
        return healthField;
    }

    public String label() {
        return label;
    }
}
