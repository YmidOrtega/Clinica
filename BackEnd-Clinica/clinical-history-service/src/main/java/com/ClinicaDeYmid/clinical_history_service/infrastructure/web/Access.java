package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

final class Access {

    static final String CLINICAL_STAFF = "hasAnyRole('DOCTOR', 'NURSE')";

    private Access() {
    }
}
