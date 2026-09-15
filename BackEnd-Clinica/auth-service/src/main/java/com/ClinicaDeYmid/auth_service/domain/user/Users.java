package com.ClinicaDeYmid.auth_service.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Users {

    User save(User user);

    Optional<User> findByUuid(UUID uuid);

    Optional<User> findByEmail(EmailAddress email);

    boolean existsByEmail(EmailAddress email);

    long countActiveWithRole(Role role);

    boolean anyNotDeactivatedWithRole(Role role);

    List<UUID> lockActiveWithRole(Role role);

    Page<User> search(Criteria criteria, Pageable pageable);

    record Criteria(String text, Role role, UserStatus.Code status) {

        public Criteria {
            text = text == null || text.isBlank() ? null : text.strip();
        }
    }
}
