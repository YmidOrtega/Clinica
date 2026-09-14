package com.ClinicaDeYmid.clinical_history_service.domain.update;

public enum ListItemStatus {
    ACTIVE,
    INACTIVE,
    RESOLVED,
    ENTERED_IN_ERROR;

    public boolean canChangeTo(ListItemStatus next) {
        return switch (this) {
            case ENTERED_IN_ERROR -> false;
            case ACTIVE, INACTIVE, RESOLVED -> next != null && next != this;
        };
    }
}
