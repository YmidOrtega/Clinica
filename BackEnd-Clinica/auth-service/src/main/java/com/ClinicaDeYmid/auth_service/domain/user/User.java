package com.ClinicaDeYmid.auth_service.domain.user;

import com.ClinicaDeYmid.auth_service.domain.password.PasswordHash;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "users")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Embedded
    private EmailAddress email;

    @Embedded
    private FullName fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus.Code statusCode;

    @Column(name = "status_reason", length = UserStatus.MAX_REASON_LENGTH)
    private String statusReason;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "status_changed_by", length = 36)
    private UUID statusChangedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_changed_by_role", length = 20)
    private Role statusChangedByRole;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @NotAudited
    @Column(name = "password_hash", length = PasswordHash.MAX_LENGTH)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_state", nullable = false, length = 20)
    private CredentialState.Code credentialStateCode;

    @Column(name = "credential_reason", length = UserStatus.MAX_REASON_LENGTH)
    private String credentialReason;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "second_factor_state", nullable = false, length = 20)
    private SecondFactorState.Code secondFactorCode;

    @Column(name = "second_factor_enrolled_at")
    private Instant secondFactorEnrolledAt;

    @Column(name = "second_factor_reset_reason", length = UserStatus.MAX_REASON_LENGTH)
    private String secondFactorResetReason;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "second_factor_reset_by", length = 36)
    private UUID secondFactorResetBy;

    @Column(name = "second_factor_reset_at")
    private Instant secondFactorResetAt;

    @Column(name = "tokens_not_before", nullable = false)
    private Instant tokensNotBefore;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private final List<UserEvent> events = new ArrayList<>();

    protected User() {
    }

    public static User invite(EmailAddress email, FullName fullName, Role role, Actor invitedBy, Clock clock) {
        DomainRules.required(email, "email");
        DomainRules.required(fullName, "fullName");
        DomainRules.required(role, "role");
        DomainRules.required(invitedBy, "invitedBy");
        requireManageable(role, invitedBy);
        Instant now = now(clock);
        User user = new User();
        user.uuid = UUID.randomUUID();
        user.email = email;
        user.fullName = fullName;
        user.role = role;
        user.applyStatus(new UserStatus.PendingActivation(), now);
        user.applyCredential(new CredentialState.NotSet(), null);
        user.secondFactorCode = SecondFactorState.Code.NOT_ENROLLED;
        user.tokensNotBefore = now;
        user.events.add(new UserEvent.Invited(invitedBy));
        return user;
    }

    public static User bootstrapSuperAdmin(EmailAddress email, FullName fullName, Clock clock) {
        DomainRules.required(email, "email");
        DomainRules.required(fullName, "fullName");
        Instant now = now(clock);
        User user = new User();
        user.uuid = UUID.randomUUID();
        user.email = email;
        user.fullName = fullName;
        user.role = Role.SUPER_ADMIN;
        user.applyStatus(new UserStatus.PendingActivation(), now);
        user.applyCredential(new CredentialState.NotSet(), null);
        user.secondFactorCode = SecondFactorState.Code.NOT_ENROLLED;
        user.tokensNotBefore = now;
        user.events.add(new UserEvent.Bootstrapped());
        return user;
    }

    public void activate(PasswordHash hash, Clock clock) {
        DomainRules.required(hash, "passwordHash");
        Instant now = now(clock);
        applyStatus(status().activate(), now);
        applyCredential(new CredentialState.Current(now), hash);
        events.add(new UserEvent.Activated());
    }

    public void changePassword(PasswordHash hash, Clock clock) {
        replaceCredential(hash, clock);
        events.add(new UserEvent.PasswordChanged());
    }

    public void rehashPassword(PasswordHash hash) {
        DomainRules.required(hash, "passwordHash");
        requireActive();
        passwordHash = hash.value();
    }

    public void resetPassword(PasswordHash hash, Clock clock) {
        replaceCredential(hash, clock);
        tokensNotBefore = now(clock);
        events.add(new UserEvent.PasswordReset());
    }

    public void requirePasswordChange(String reason, Actor actor, Clock clock) {
        requireManageableBy(actor);
        requireActive();
        Instant changedAt = passwordChangedAt;
        CredentialState.ChangeRequired required = new CredentialState.ChangeRequired(changedAt, reason);
        applyCredential(required, currentHash().orElseThrow());
        tokensNotBefore = now(clock);
        events.add(new UserEvent.PasswordChangeRequired(required.reason(), actor));
    }

    public void rename(FullName newName, Actor actor) {
        DomainRules.required(newName, "fullName");
        if (!actor.uuid().equals(uuid)) {
            requireManageable(role, actor);
        }
        if (!newName.equals(fullName)) {
            fullName = newName;
            events.add(new UserEvent.Renamed(actor));
        }
    }

    public void changeRole(Role newRole, Actor actor, Clock clock) {
        DomainRules.required(newRole, "role");
        requireManageableBy(actor);
        requireManageable(newRole, actor);
        if (newRole != role) {
            Role previousRole = role;
            role = newRole;
            tokensNotBefore = now(clock);
            events.add(new UserEvent.RoleChanged(previousRole, actor));
        }
    }

    public void suspend(String reason, Actor actor, Clock clock) {
        requireManageableBy(actor);
        Instant now = now(clock);
        applyStatus(status().suspend(reason, actor, now), now);
        tokensNotBefore = now;
        events.add(new UserEvent.Suspended(statusReason, actor));
    }

    public void deactivate(String reason, Actor actor, Clock clock) {
        requireManageableBy(actor);
        Instant now = now(clock);
        applyStatus(status().deactivate(reason, actor, now), now);
        tokensNotBefore = now;
        events.add(new UserEvent.Deactivated(statusReason, actor));
    }

    public void reactivate(Actor actor, Clock clock) {
        requireManageableBy(actor);
        applyStatus(status().reactivate(passwordHash != null), now(clock));
        events.add(new UserEvent.Reactivated(actor));
    }

    public void enrollTotp(Clock clock) {
        requireActive();
        if (!(secondFactorState() instanceof SecondFactorState.NotEnrolled)) {
            throw new UserException.SecondFactorAlreadyEnrolled();
        }
        secondFactorCode = SecondFactorState.Code.TOTP_ENROLLED;
        secondFactorEnrolledAt = now(clock);
        events.add(new UserEvent.SecondFactorEnrolled());
    }

    public void resetSecondFactor(String reason, Actor actor, Clock clock) {
        requireManageableBy(actor);
        if (!(secondFactorState() instanceof SecondFactorState.TotpEnrolled)) {
            throw new UserException.SecondFactorNotEnrolled();
        }
        Instant now = now(clock);
        secondFactorCode = SecondFactorState.Code.NOT_ENROLLED;
        secondFactorEnrolledAt = null;
        secondFactorResetReason = DomainRules.requiredText(reason, "reason", UserStatus.MIN_REASON_LENGTH, UserStatus.MAX_REASON_LENGTH);
        secondFactorResetBy = actor.uuid();
        secondFactorResetAt = now;
        tokensNotBefore = now;
        events.add(new UserEvent.SecondFactorReset(secondFactorResetReason, actor));
    }

    public void revokeSessions(Actor actor, Clock clock) {
        DomainRules.required(actor, "actor");
        if (!actor.uuid().equals(uuid)) {
            requireManageableBy(actor);
        }
        tokensNotBefore = now(clock);
        events.add(new UserEvent.SessionsRevoked(actor));
    }

    public List<UserEvent> pullEvents() {
        List<UserEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    public boolean mayAuthenticate() {
        return status() instanceof UserStatus.Active;
    }

    public UserStatus status() {
        return switch (statusCode) {
            case PENDING_ACTIVATION -> new UserStatus.PendingActivation();
            case ACTIVE -> new UserStatus.Active();
            case SUSPENDED -> new UserStatus.Suspended(statusReason, new Actor(statusChangedBy, statusChangedByRole), statusChangedAt);
            case DEACTIVATED -> new UserStatus.Deactivated(statusReason, new Actor(statusChangedBy, statusChangedByRole), statusChangedAt);
        };
    }

    public CredentialState credentialState() {
        return switch (credentialStateCode) {
            case NOT_SET -> new CredentialState.NotSet();
            case CURRENT -> new CredentialState.Current(passwordChangedAt);
            case CHANGE_REQUIRED -> new CredentialState.ChangeRequired(passwordChangedAt, credentialReason);
        };
    }

    public SecondFactorState secondFactorState() {
        return switch (secondFactorCode) {
            case NOT_ENROLLED -> new SecondFactorState.NotEnrolled();
            case TOTP_ENROLLED -> new SecondFactorState.TotpEnrolled(secondFactorEnrolledAt);
        };
    }

    public Optional<PasswordHash> currentHash() {
        return Optional.ofNullable(passwordHash).map(PasswordHash::new);
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public EmailAddress email() {
        return email;
    }

    public FullName fullName() {
        return fullName;
    }

    public Role role() {
        return role;
    }

    public Instant statusChangedAt() {
        return statusChangedAt;
    }

    public Instant tokensNotBefore() {
        return tokensNotBefore;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void requireManageableBy(Actor actor) {
        DomainRules.required(actor, "actor");
        if (actor.uuid().equals(uuid)) {
            throw new UserException.SelfManagement();
        }
        requireManageable(role, actor);
    }

    private void replaceCredential(PasswordHash hash, Clock clock) {
        DomainRules.required(hash, "passwordHash");
        requireActive();
        applyCredential(new CredentialState.Current(now(clock)), hash);
    }

    private void requireActive() {
        if (!(status() instanceof UserStatus.Active)) {
            throw new UserException.NotActive();
        }
    }

    private void applyStatus(UserStatus status, Instant changedAt) {
        statusCode = status.code();
        statusChangedAt = changedAt;
        switch (status) {
            case UserStatus.PendingActivation pending -> clearStatusDetails();
            case UserStatus.Active active -> clearStatusDetails();
            case UserStatus.Suspended suspended -> applyStatusDetails(suspended.reason(), suspended.by());
            case UserStatus.Deactivated deactivated -> applyStatusDetails(deactivated.reason(), deactivated.by());
        }
    }

    private void applyStatusDetails(String reason, Actor by) {
        statusReason = reason;
        statusChangedBy = by.uuid();
        statusChangedByRole = by.role();
    }

    private void clearStatusDetails() {
        statusReason = null;
        statusChangedBy = null;
        statusChangedByRole = null;
    }

    private void applyCredential(CredentialState state, PasswordHash hash) {
        credentialStateCode = state.code();
        switch (state) {
            case CredentialState.NotSet notSet -> {
                passwordHash = null;
                passwordChangedAt = null;
                credentialReason = null;
            }
            case CredentialState.Current current -> {
                passwordHash = hash.value();
                passwordChangedAt = current.changedAt();
                credentialReason = null;
            }
            case CredentialState.ChangeRequired changeRequired -> {
                passwordHash = hash.value();
                passwordChangedAt = changeRequired.changedAt();
                credentialReason = changeRequired.reason();
            }
        }
    }

    private static void requireManageable(Role target, Actor actor) {
        if (!target.manageableBy(actor.role())) {
            throw new UserException.RoleNotManageable(actor.role(), target);
        }
    }

    private static Instant now(Clock clock) {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof User user && uuid != null && uuid.equals(user.uuid);
    }

    @Override
    public int hashCode() {
        return User.class.hashCode();
    }
}
