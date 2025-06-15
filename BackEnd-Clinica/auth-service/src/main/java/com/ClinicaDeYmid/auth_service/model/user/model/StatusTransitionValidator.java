package com.ClinicaDeYmid.auth_service.model.user.model;

import com.ClinicaDeYmid.auth_service.model.user.enums.StatusUser;

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
