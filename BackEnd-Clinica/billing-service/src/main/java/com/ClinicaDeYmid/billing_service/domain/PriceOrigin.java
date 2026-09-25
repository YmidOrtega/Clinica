package com.ClinicaDeYmid.billing_service.domain;

public enum PriceOrigin {

    PACKAGE(false),
    CONTRACT_EXCEPTION(true),
    TARIFF_MANUAL(true),
    SURGICAL_LIQUIDATION(true),
    CAPITATION(false),
    GLOBAL_BUDGET(false),
    UNPRICED(false),
    MANUAL(true);

    private final boolean billablePerService;

    PriceOrigin(boolean billablePerService) {
        this.billablePerService = billablePerService;
    }

    public boolean billablePerService() {
        return billablePerService;
    }
}
