package com.ClinicaDeYmid.contracting_service.infrastructure.web;

final class Access {

    static final String READ = "hasAuthority('contracting:read')";
    static final String MANAGE_PAYERS = "hasAuthority('contracting:manage-payers')";
    static final String MANAGE_PORTFOLIO = "hasAuthority('contracting:manage-tariffs')";
    static final String MANAGE_TARIFFS = "hasAuthority('contracting:manage-tariffs')";
    static final String MANAGE_CONTRACTS = "hasAuthority('contracting:manage-contracts')";

    private Access() {
    }
}
