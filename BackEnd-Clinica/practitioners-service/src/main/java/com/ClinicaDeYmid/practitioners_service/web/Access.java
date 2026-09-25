package com.ClinicaDeYmid.practitioners_service.web;

final class Access {

    static final String READ = "hasAuthority('practitioners:read')";
    static final String MANAGE = "hasAuthority('practitioners:manage')";
    static final String MANAGE_FEES = "hasAuthority('practitioners:manage-fees')";
    static final String READ_FEES = "hasAuthority('practitioners:read-fees')";

    private Access() {
    }
}
