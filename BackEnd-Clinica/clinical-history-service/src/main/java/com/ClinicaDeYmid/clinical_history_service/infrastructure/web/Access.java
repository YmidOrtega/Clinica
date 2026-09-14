package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

final class Access {

    static final String CLINICAL_STAFF = "hasAnyRole('DOCTOR', 'NURSE')";
    static final String VERIFY_INTEGRITY = "hasAnyRole('DOCTOR', 'NURSE', 'MEDICAL_RECORDS', 'ADMIN', 'SUPER_ADMIN')";

    private Access() {
    }
}
