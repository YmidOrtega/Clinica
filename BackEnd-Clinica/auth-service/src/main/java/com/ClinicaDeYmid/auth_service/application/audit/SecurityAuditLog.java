package com.ClinicaDeYmid.auth_service.application.audit;

public interface SecurityAuditLog {

    void record(SecurityEvent event);
}
