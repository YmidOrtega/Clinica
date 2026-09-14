package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

final class Access {

    static final String CLINICAL_STAFF = "hasAnyRole('DOCTOR', 'NURSE')";
    static final String RECORDS_OFFICE = "hasRole('MEDICAL_RECORDS')";
    static final String MANAGE_KEYS = "hasRole('SUPER_ADMIN')";
    static final String MANAGE_CATALOGS = "hasRole('SUPER_ADMIN')";
    static final String VERIFY_INTEGRITY = "hasAnyRole('DOCTOR', 'NURSE', 'MEDICAL_RECORDS', 'ADMIN', 'SUPER_ADMIN')";

    private Access() {
    }
}
