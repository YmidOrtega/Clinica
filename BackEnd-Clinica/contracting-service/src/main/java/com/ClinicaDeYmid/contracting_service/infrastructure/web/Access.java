package com.ClinicaDeYmid.contracting_service.infrastructure.web;

final class Access {

    static final String READ = "hasAuthority('contracting:read')";
    static final String MANAGE_PAYERS = "hasAuthority('contracting:manage-payers')";
    static final String MANAGE_PORTFOLIO = "hasAuthority('contracting:manage-tariffs')";
    static final String MANAGE_TARIFFS = "hasAuthority('contracting:manage-tariffs')";
    static final String MANAGE_CONTRACTS = "hasAuthority('contracting:manage-contracts')";
    static final String MANAGE_CAPITATION = "hasAuthority('contracting:manage-capitation')";
    static final String QUOTE_PRICES = "hasAuthority('contracting:quote-prices')";

    private Access() {
    }
}
