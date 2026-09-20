package com.ClinicaDeYmid.practitioners_service.web;

final class Access {

    static final String READ = "hasAuthority('practitioners:read')";
    static final String MANAGE = "hasAuthority('practitioners:manage')";

    private Access() {
    }
}
