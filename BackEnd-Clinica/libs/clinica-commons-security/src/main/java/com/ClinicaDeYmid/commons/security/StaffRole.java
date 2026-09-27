package com.ClinicaDeYmid.commons.security;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum StaffRole {

    SUPER_ADMIN(EnumSet.allOf(StaffPermission.class)),
    ADMIN(EnumSet.allOf(StaffPermission.class)),
    CONTRACTING(EnumSet.of(
            StaffPermission.CONTRACTING_READ,
            StaffPermission.CONTRACTING_MANAGE_PAYERS,
            StaffPermission.CONTRACTING_MANAGE_CONTRACTS,
            StaffPermission.CONTRACTING_MANAGE_TARIFFS,
            StaffPermission.CONTRACTING_MANAGE_CAPITATION,
            StaffPermission.CONTRACTING_QUOTE_PRICES)),
    BILLING(EnumSet.of(
            StaffPermission.CONTRACTING_READ,
            StaffPermission.CONTRACTING_QUOTE_PRICES,
            StaffPermission.ADMISSIONS_READ,
            StaffPermission.PRACTITIONERS_READ,
            StaffPermission.PRACTITIONERS_READ_FEES,
            StaffPermission.BILLING_READ,
            StaffPermission.BILLING_SELL,
            StaffPermission.BILLING_PRICE_MANUALLY,
            StaffPermission.BILLING_INVOICE,
            StaffPermission.BILLING_VOID,
            StaffPermission.BILLING_COLLECT,
            StaffPermission.BILLING_FILE,
            StaffPermission.BILLING_GLOSSES,
            StaffPermission.ASSISTANT_USE)),
    ACCOUNTS_RECEIVABLE(EnumSet.of(
            StaffPermission.CONTRACTING_READ,
            StaffPermission.ADMISSIONS_READ,
            StaffPermission.PRACTITIONERS_READ,
            StaffPermission.BILLING_READ,
            StaffPermission.BILLING_FILE,
            StaffPermission.BILLING_GLOSSES,
            StaffPermission.ASSISTANT_USE)),
    HUMAN_RESOURCES(EnumSet.of(
            StaffPermission.PRACTITIONERS_READ,
            StaffPermission.PRACTITIONERS_MANAGE,
            StaffPermission.PRACTITIONERS_MANAGE_FEES,
            StaffPermission.PRACTITIONERS_READ_FEES)),
    RECEPTIONIST(EnumSet.of(
            StaffPermission.CONTRACTING_READ,
            StaffPermission.PRACTITIONERS_READ,
            StaffPermission.ADMISSIONS_READ,
            StaffPermission.ADMISSIONS_ADMIT)),
    DOCTOR(EnumSet.of(
            StaffPermission.ADMISSIONS_READ,
            StaffPermission.ADMISSIONS_DISCHARGE)),
    NURSE(EnumSet.of(
            StaffPermission.ADMISSIONS_READ,
            StaffPermission.ADMISSIONS_MOVE_BED)),
    MEDICAL_RECORDS(EnumSet.of(StaffPermission.ADMISSIONS_READ));

    private final Set<StaffPermission> permissions;

    StaffRole(Set<StaffPermission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<StaffPermission> permissions() {
        return permissions;
    }

    public static Optional<StaffRole> of(String name) {
        if (name == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    public static Set<StaffPermission> permissionsOf(String name) {
        return of(name).map(StaffRole::permissions).orElseGet(() -> Set.of());
    }
}
