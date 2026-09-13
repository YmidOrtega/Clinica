package com.ClinicaDeYmid.patient_service.infrastructure.web;

final class Access {

    static final String READ = "hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST')";
    static final String MANAGE = "hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')";
    static final String CHANGE_STATUS = "hasAnyRole('SUPER_ADMIN', 'ADMIN')";
    static final String RECORD_DEATH = "hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DOCTOR')";
    static final String AUDIT = "hasAnyRole('SUPER_ADMIN', 'ADMIN')";

    private Access() {
    }
}
