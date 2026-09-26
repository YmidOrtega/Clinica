package com.ClinicaDeYmid.billing_service.application.rips;

import com.ClinicaDeYmid.billing_service.domain.DischargeType;
import com.ClinicaDeYmid.billing_service.domain.SharedPaymentKind;

import java.util.Map;

final class RipsCodes {

    static final String COLOMBIA = "170";
    static final String NO_DISABILITY = "NO";
    static final String NOT_APPLICABLE_COLLECTION = "05";
    static final String STAYS = "03";
    static final String COMPLEMENTARY_SERVICES = "04";
    static final String STILL_IN_SERVICE = "08";
    static final String SPONTANEOUS_DEMAND = "01";
    static final String FROM_OUTPATIENT = "02";
    static final String FROM_EMERGENCY = "03";
    static final String FROM_HOSPITALIZATION = "04";
    static final String PRIVATE_USER = "12";

    private static final Map<String, String> DOCUMENT_TYPES = Map.ofEntries(
            Map.entry("CEDULA_DE_CIUDADANIA", "CC"),
            Map.entry("CEDULA_DE_EXTRANJERIA", "CE"),
            Map.entry("TARJETA_DE_IDENTIDAD", "TI"),
            Map.entry("REGISTRO_CIVIL", "RC"),
            Map.entry("PASAPORTE", "PA"),
            Map.entry("PERMISO_ESPECIAL_DE_PERMANENCIA", "PE"),
            Map.entry("PERMISO_POR_PROTECCION_TEMPORAL", "PT"),
            Map.entry("DOCUMENTO_EXTRANJERO", "DE"),
            Map.entry("NIT", "NI"));
    private static final Map<String, String> SEXES = Map.of("FEMALE", "M", "MALE", "H", "INDETERMINATE", "I");
    private static final Map<String, String> ZONES = Map.of("URBAN", "02", "RURAL", "01");
    private static final Map<String, String> DIAGNOSIS_TYPES = Map.of(
            "IMPRESSION", "01", "CONFIRMED_NEW", "02", "CONFIRMED_REPEATED", "03");

    private RipsCodes() {
    }

    static String documentType(String type, boolean minor) {
        if ("NO_IDENTIFICADO".equals(type)) {
            return minor ? "MS" : "AS";
        }
        return type == null ? null : DOCUMENT_TYPES.get(type);
    }

    static String sex(String sex) {
        return sex == null ? null : SEXES.get(sex);
    }

    static String zone(String zone) {
        return zone == null ? null : ZONES.get(zone);
    }

    static String diagnosisType(String type) {
        return type == null ? null : DIAGNOSIS_TYPES.get(type);
    }

    static String userType(String regime, String affiliateType) {
        if (regime == null) {
            return null;
        }
        boolean beneficiary = "BENEFICIARY".equals(affiliateType);
        return switch (regime) {
            case "CONTRIBUTORY" -> affiliateType == null ? null : beneficiary ? "02" : "01";
            case "SUBSIDIZED" -> "04";
            case "SPECIAL" -> affiliateType == null ? null : beneficiary ? "07" : "06";
            case "UNINSURED" -> "05";
            default -> null;
        };
    }

    static String destination(DischargeType discharge) {
        if (discharge == null) {
            return STILL_IN_SERVICE;
        }
        return switch (discharge) {
            case MEDICAL, VOLUNTARY, ESCAPE -> "01";
            case DEATH -> "02";
            case REFERRAL -> "04";
        };
    }

    static String collection(SharedPaymentKind kind) {
        return switch (kind) {
            case COPAYMENT -> "01";
            case MODERATING_FEE -> "02";
            case VOLUNTARY_PLAN -> "03";
            case RECOVERY_FEE -> NOT_APPLICABLE_COLLECTION;
        };
    }
}
