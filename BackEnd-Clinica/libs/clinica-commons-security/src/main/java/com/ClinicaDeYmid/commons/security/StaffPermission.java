package com.ClinicaDeYmid.commons.security;

public enum StaffPermission {

    CONTRACTING_READ("contracting:read"),
    CONTRACTING_MANAGE_PAYERS("contracting:manage-payers"),
    CONTRACTING_MANAGE_CONTRACTS("contracting:manage-contracts"),
    CONTRACTING_MANAGE_TARIFFS("contracting:manage-tariffs"),
    CONTRACTING_MANAGE_CAPITATION("contracting:manage-capitation"),
    CONTRACTING_QUOTE_PRICES("contracting:quote-prices"),
    PRACTITIONERS_READ("practitioners:read"),
    PRACTITIONERS_MANAGE("practitioners:manage"),
    PRACTITIONERS_MANAGE_FEES("practitioners:manage-fees"),
    PRACTITIONERS_READ_FEES("practitioners:read-fees"),
    ADMISSIONS_READ("admissions:read"),
    ADMISSIONS_ADMIT("admissions:admit"),
    ADMISSIONS_MOVE_BED("admissions:move-bed"),
    ADMISSIONS_DISCHARGE("admissions:discharge"),
    ADMISSIONS_CANCEL("admissions:cancel"),
    ADMISSIONS_MANAGE_BEDS("admissions:manage-beds"),
    ADMISSIONS_OVERRIDE_COVERAGE("admissions:override-coverage"),
    BILLING_READ("billing:read"),
    BILLING_SELL("billing:sell"),
    BILLING_PRICE_MANUALLY("billing:price-manually"),
    BILLING_INVOICE("billing:invoice"),
    BILLING_VOID("billing:void"),
    BILLING_COLLECT("billing:collect"),
    BILLING_FILE("billing:file"),
    BILLING_GLOSSES("billing:glosses"),
    BILLING_MANAGE_CONFIG("billing:manage-config");

    private final String code;

    StaffPermission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
