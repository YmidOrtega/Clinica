package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.domain.user.EmailAddress;
import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserEvent;
import com.ClinicaDeYmid.auth_service.domain.user.UserException;
import com.ClinicaDeYmid.auth_service.domain.user.UserStatus;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import com.ClinicaDeYmid.auth_service.infrastructure.events.JdbcAuthEventOutbox;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaUsers implements Users {

    private static final String UNIQUE_EMAIL = "uk_users_email";

    private final UserJpaRepository repository;
    private final JdbcAuthEventOutbox outbox;

    JpaUsers(UserJpaRepository repository, JdbcAuthEventOutbox outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    @Override
    @Transactional
    public User save(User user) {
        List<UserEvent> events = user.pullEvents();
        try {
            User saved = repository.saveAndFlush(user);
            outbox.appendUserEvents(saved, events);
            return saved;
        } catch (DataIntegrityViolationException violation) {
            if (String.valueOf(violation.getMostSpecificCause().getMessage()).contains(UNIQUE_EMAIL)) {
                throw new UserException.EmailAlreadyRegistered();
            }
            throw violation;
        }
    }

    @Override
    public Optional<User> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return repository.findByEmail(email.value());
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return repository.existsByEmail(email.value());
    }

    @Override
    public long countActiveWithRole(Role role) {
        return repository.countByRoleAndStatus(role, UserStatus.Code.ACTIVE);
    }

    @Override
    public boolean anyNotDeactivatedWithRole(Role role) {
        return repository.existsByRoleAndStatusNot(role, UserStatus.Code.DEACTIVATED);
    }

    @Override
    public List<UUID> lockActiveWithRole(Role role) {
        return repository.lockUuidsByRoleAndStatus(role.name(), UserStatus.Code.ACTIVE.name()).stream().map(UUID::fromString).toList();
    }

    @Override
    public Page<User> search(Criteria criteria, Pageable pageable) {
        String prefix = criteria.text() == null ? null : criteria.text().replaceAll("([\\\\%_])", "\\\\$1") + "%";
        Pageable ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("fullName.value", "email.value"));
        return repository.search(prefix, criteria.role(), criteria.status(), ordered);
    }
}
