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
            StaffPermission.CONTRACTING_QUOTE_PRICES)),
    RECEPTIONIST(EnumSet.of(StaffPermission.CONTRACTING_READ)),
    DOCTOR(EnumSet.noneOf(StaffPermission.class)),
    NURSE(EnumSet.noneOf(StaffPermission.class)),
    MEDICAL_RECORDS(EnumSet.noneOf(StaffPermission.class));

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
