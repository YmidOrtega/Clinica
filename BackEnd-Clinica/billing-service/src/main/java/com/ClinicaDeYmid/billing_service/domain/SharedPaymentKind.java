package com.ClinicaDeYmid.billing_service.domain;

public enum SharedPaymentKind {

    COPAYMENT("COPAGO", "Copago", "01"),
    MODERATING_FEE("CUOTA_MODERADORA", "Cuota moderadora", "02"),
    VOLUNTARY_PLAN("PAGOS_COMPARTIDOS", "Pago compartido de plan voluntario", "03");

    static final String CONTRIBUTORY = "CONTRIBUTORY";

    private final String healthField;
    private final String label;
    private final String collectionConcept;

    SharedPaymentKind(String healthField, String label, String collectionConcept) {
        this.healthField = healthField;
        this.label = label;
        this.collectionConcept = collectionConcept;
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

    public String collectionConcept() {
        return collectionConcept;
    }
}
