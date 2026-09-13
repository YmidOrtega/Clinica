package com.ClinicaDeYmid.patient_service.support;

import java.util.concurrent.ThreadLocalRandom;

public final class PatientJson {

    public static final String PROVIDER_NIT = "900123456-7";

    private PatientJson() {
    }

    public static String uniqueCedula() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 1_999_999_999L));
    }

    public static String registration(String documentNumber, String healthProviderNit) {
        return """
                {
                  "document": {"type": "CEDULA_DE_CIUDADANIA", "number": "%s"},
                  "demographics": {"firstNames": "Ana María", "lastNames": "Restrepo Gómez", "birthDate": "1990-04-12",
                                   "sex": "FEMALE", "countryOfOrigin": "CO", "disability": "NONE"},
                  "contact": {"mobile": "3001234567", "email": "ana@example.com"},
                  "affiliation": {"regime": "CONTRIBUTORY", "affiliateType": "HOLDER", "healthProviderNit": "%s"},
                  "residence": {"department": "Santander", "municipality": "Bucaramanga", "zone": "URBAN", "address": "Calle 45 # 27-10"}
                }
                """.formatted(documentNumber, healthProviderNit);
    }

    public static String uninsuredRegistration(String documentNumber) {
        return """
                {
                  "document": {"type": "CEDULA_DE_CIUDADANIA", "number": "%s"},
                  "demographics": {"firstNames": "Luis", "lastNames": "Pérez", "birthDate": "1985-01-20",
                                   "sex": "MALE", "countryOfOrigin": "CO", "disability": "NONE"},
                  "contact": {"mobile": "3017654321"},
                  "affiliation": {"regime": "UNINSURED"},
                  "residence": {"department": "Santander", "municipality": "Girón", "zone": "RURAL", "address": "Vereda El Carmen"}
                }
                """.formatted(documentNumber);
    }

    public static String healthProvider(String nit) {
        return """
                {"nit": "%s", "socialReason": "Salud Total EPS S.A.", "typeProvider": "EPS", "contracts": [], "contractStatus": "ACTIVE"}
                """.formatted(nit.replace("-", ""));
    }
}
