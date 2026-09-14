package com.ClinicaDeYmid.auth_service.domain.user;

import java.util.Optional;
import java.util.UUID;

public interface Users {

    User save(User user);

    Optional<User> findByUuid(UUID uuid);

    Optional<User> findByEmail(EmailAddress email);

    boolean existsByEmail(EmailAddress email);

    long countActiveWithRole(Role role);
}
