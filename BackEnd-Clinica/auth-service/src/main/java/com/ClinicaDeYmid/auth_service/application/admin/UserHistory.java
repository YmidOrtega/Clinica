package com.ClinicaDeYmid.auth_service.application.admin;

import com.ClinicaDeYmid.auth_service.domain.user.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserHistory {

    List<Revision> of(UUID userUuid);

    enum ChangeType {
        CREATED,
        UPDATED
    }

    record Revision(long number, Instant revisedAt, String revisedBy, ChangeType changeType, User state) {
    }
}
