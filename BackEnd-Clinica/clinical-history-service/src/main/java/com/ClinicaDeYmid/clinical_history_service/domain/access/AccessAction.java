package com.ClinicaDeYmid.clinical_history_service.domain.access;

public enum AccessAction {
    OPEN_ENCOUNTER,
    LIST_ENCOUNTERS,
    READ_ENCOUNTER,
    READ_NOTE,
    READ_LISTS,
    READ_VITAL_SIGNS,
    READ_ATTACHMENT,
    EXPORT_RECORD,
    WRITE_NOTE,
    VOID_NOTE,
    CLOSE_ENCOUNTER,
    ADD_CARE_TEAM_MEMBER,
    EMERGENCY_ACCESS
}
