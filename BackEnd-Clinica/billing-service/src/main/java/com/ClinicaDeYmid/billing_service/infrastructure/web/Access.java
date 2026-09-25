package com.ClinicaDeYmid.billing_service.infrastructure.web;

final class Access {

    static final String READ = "hasAuthority('billing:read')";
    static final String PREPARE_SALE = "hasAuthority('billing:sell') or hasAuthority('billing:read')";
    static final String SELL = "hasAuthority('billing:sell')";
    static final String MANAGE_CONFIG = "hasAuthority('billing:manage-config')";

    private Access() {
    }
}
