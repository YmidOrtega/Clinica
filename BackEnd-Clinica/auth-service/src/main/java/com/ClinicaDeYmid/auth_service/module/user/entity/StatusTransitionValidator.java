package com.ClinicaDeYmid.auth_service.module.user.entity;

import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;

public class StatusTransitionValidator {
    public static boolean canTransition(StatusUser current, StatusUser next) {

        switch (current) {
            case PENDING:
                return next == StatusUser.ACTIVE || next == StatusUser.INACTIVE;
            case ACTIVE:
                return next == StatusUser.INACTIVE || next == StatusUser.SUSPENDED || next == StatusUser.DELETED;
            case INACTIVE:
                return next == StatusUser.ACTIVE;
            case SUSPENDED:
                return next == StatusUser.ACTIVE || next == StatusUser.DELETED;
            case DELETED:
                return false;
            default:
                return false;
        }
    }

}
