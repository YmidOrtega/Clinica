package com.ClinicaDeYmid.commons.security;

public enum StaffPermission {

    CONTRACTING_READ("contracting:read"),
    CONTRACTING_MANAGE_PAYERS("contracting:manage-payers"),
    CONTRACTING_MANAGE_CONTRACTS("contracting:manage-contracts"),
    CONTRACTING_MANAGE_TARIFFS("contracting:manage-tariffs"),
    CONTRACTING_MANAGE_CAPITATION("contracting:manage-capitation"),
    CONTRACTING_QUOTE_PRICES("contracting:quote-prices");

    private final String code;

    StaffPermission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
