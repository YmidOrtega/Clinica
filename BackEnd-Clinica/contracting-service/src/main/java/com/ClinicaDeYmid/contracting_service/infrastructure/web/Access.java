package com.ClinicaDeYmid.contracting_service.infrastructure.web;

final class Access {

    static final String READ = "hasAuthority('contracting:read')";
    static final String MANAGE_PAYERS = "hasAuthority('contracting:manage-payers')";

    private Access() {
    }
}
