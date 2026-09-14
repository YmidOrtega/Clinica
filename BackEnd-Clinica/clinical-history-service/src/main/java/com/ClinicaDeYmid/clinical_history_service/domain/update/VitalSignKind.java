package com.ClinicaDeYmid.clinical_history_service.domain.update;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;

import java.math.BigDecimal;

public enum VitalSignKind {
    HEART_RATE("lpm", "20", "300", 0),
    RESPIRATORY_RATE("rpm", "4", "80", 0),
    SYSTOLIC_BLOOD_PRESSURE("mmHg", "40", "300", 0),
    DIASTOLIC_BLOOD_PRESSURE("mmHg", "20", "200", 0),
    TEMPERATURE("°C", "25", "45", 1),
    OXYGEN_SATURATION("%", "50", "100", 0),
    WEIGHT("kg", "0.2", "500", 2),
    HEIGHT("cm", "20", "250", 1),
    GLASGOW_COMA_SCALE("puntos", "3", "15", 0),
    PAIN_SCALE("puntos", "0", "10", 0);

    private final String unit;
    private final BigDecimal min;
    private final BigDecimal max;
    private final int scale;

    VitalSignKind(String unit, String min, String max, int scale) {
        this.unit = unit;
        this.min = new BigDecimal(min);
        this.max = new BigDecimal(max);
        this.scale = scale;
    }

    public String unit() {
        return unit;
    }

    public BigDecimal validate(BigDecimal value) {
        if (value == null) {
            throw new ClinicalException.InvalidData("readings.value", "es obligatorio para " + name());
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (Math.max(normalized.scale(), 0) > scale) {
            throw new ClinicalException.InvalidData("readings.value", name() + " admite máximo " + scale + " decimales");
        }
        if (normalized.compareTo(min) < 0 || normalized.compareTo(max) > 0) {
            throw new ClinicalException.InvalidData("readings.value",
                    name() + " debe estar entre " + min.toPlainString() + " y " + max.toPlainString() + " " + unit);
        }
        return normalized.setScale(Math.max(normalized.scale(), 0));
    }
}
