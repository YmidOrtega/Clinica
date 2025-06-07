package com.ClinicaDeYmid.patient_service.module.entity;

import com.ClinicaDeYmid.patient_service.module.enums.Status;

public class StatusTransitionValidator {
    public static boolean canTransition(Status current, Status next) {

        switch (current) {
            case ALIVE:
                return next == Status.DECEASED || next == Status.SUSPENDED || next == Status.DELETED;
            case DECEASED:
                return false;
            case SUSPENDED:
                return next == Status.ALIVE || next == Status.DELETED;
            case DELETED:
                return false;
            default:
                throw new IllegalArgumentException("Unknown status: " + current);
        }
    }
}
