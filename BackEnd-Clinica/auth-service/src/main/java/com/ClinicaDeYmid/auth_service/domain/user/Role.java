package com.ClinicaDeYmid.auth_service.domain.user;

public enum Role {
    SUPER_ADMIN,
    ADMIN,
    DOCTOR,
    NURSE,
    RECEPTIONIST,
    MEDICAL_RECORDS;

    public boolean privileged() {
        return this == SUPER_ADMIN || this == ADMIN;
    }

    public boolean manageableBy(Role actor) {
        return switch (actor) {
            case SUPER_ADMIN -> true;
            case ADMIN -> !privileged();
            case DOCTOR, NURSE, RECEPTIONIST, MEDICAL_RECORDS -> false;
        };
    }
}
