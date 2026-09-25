package com.ClinicaDeYmid.billing_service.infrastructure.web;

final class Access {

    static final String READ = "hasAuthority('billing:read')";
    static final String MANAGE_CONFIG = "hasAuthority('billing:manage-config')";

    private Access() {
    }
}
