package com.ClinicaDeYmid.practitioners_service.client;

import com.ClinicaDeYmid.commons.security.StaffAccessRegistry;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class StaffAccountDirectory {

    private static final String ACTIVE = "ACTIVE";

    private final StaffAccessRegistry registry;

    public StaffAccountDirectory(StaffAccessRegistry registry) {
        this.registry = registry;
    }

    public boolean readable() {
        return registry.caughtUp();
    }

    public Optional<Account> find(UUID userUuid) {
        return registry.of(userUuid).map(access -> new Account(userUuid, ACTIVE.equals(access.status())));
    }

    public record Account(UUID userUuid, boolean active) {
    }
}
