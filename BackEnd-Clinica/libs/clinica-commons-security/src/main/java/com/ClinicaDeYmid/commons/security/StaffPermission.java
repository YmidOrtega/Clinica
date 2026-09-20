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
    ADMISSIONS_READ("admissions:read"),
    ADMISSIONS_ADMIT("admissions:admit"),
    ADMISSIONS_MOVE_BED("admissions:move-bed"),
    ADMISSIONS_DISCHARGE("admissions:discharge"),
    ADMISSIONS_CANCEL("admissions:cancel"),
    ADMISSIONS_MANAGE_BEDS("admissions:manage-beds"),
    ADMISSIONS_OVERRIDE_COVERAGE("admissions:override-coverage");

    private final String code;

    StaffPermission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
