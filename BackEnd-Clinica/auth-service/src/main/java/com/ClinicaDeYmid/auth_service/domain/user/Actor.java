package com.ClinicaDeYmid.auth_service.domain.user;

import java.util.UUID;

public record Actor(UUID uuid, Role role) {

    public Actor {
        DomainRules.required(uuid, "actor.uuid");
        DomainRules.required(role, "actor.role");
    }
}
