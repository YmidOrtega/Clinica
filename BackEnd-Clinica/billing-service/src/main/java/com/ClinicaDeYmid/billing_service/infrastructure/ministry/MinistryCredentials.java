package com.ClinicaDeYmid.billing_service.infrastructure.ministry;

public record MinistryCredentials(String documentType, String documentNumber, String password) {

    boolean configured() {
        return present(documentType) && present(documentNumber) && present(password);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "MinistryCredentials[" + documentType + " " + documentNumber + "]";
    }
}
