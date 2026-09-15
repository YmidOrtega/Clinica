package com.ClinicaDeYmid.auth_service.application.session;

import java.util.UUID;

public interface SessionRevocation {

    int revokeAll(UUID userUuid);
}
