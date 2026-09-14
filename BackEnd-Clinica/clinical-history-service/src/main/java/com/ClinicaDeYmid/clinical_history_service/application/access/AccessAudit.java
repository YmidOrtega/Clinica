package com.ClinicaDeYmid.clinical_history_service.application.access;

public interface AccessAudit {

    void record(AccessEvent event);
}
