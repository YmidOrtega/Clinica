package com.ClinicaDeYmid.auth_service.domain.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.ADMIN;
import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.CLOCK;
import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.HASH;
import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.NOW;
import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.OTHER_HASH;
import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.SUPER_ADMIN;
import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.active;
import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.at;
import static com.ClinicaDeYmid.auth_service.domain.user.UserFixtures.invited;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final Instant LATER = NOW.plusSeconds(3600);

    @Test
    void anInvitedUserWaitsForActivationWithoutACredential() {
        User user = invited(Role.NURSE);

        assertThat(user.uuid()).isNotNull();
        assertThat(user.email().value()).isEqualTo("ana.rojas@clinica.test");
        assertThat(user.status()).isEqualTo(new UserStatus.PendingActivation());
        assertThat(user.credentialState()).isEqualTo(new CredentialState.NotSet());
        assertThat(user.currentHash()).isEmpty();
        assertThat(user.mayAuthenticate()).isFalse();
        assertThat(user.tokensNotBefore()).isEqualTo(NOW);
    }

    @Test
    void activationSetsTheFirstPasswordAndAllowsAuthentication() {
        User user = invited(Role.DOCTOR);

        user.activate(HASH, at(LATER));

        assertThat(user.status()).isEqualTo(new UserStatus.Active());
        assertThat(user.credentialState()).isEqualTo(new CredentialState.Current(LATER));
        assertThat(user.currentHash()).contains(HASH);
        assertThat(user.mayAuthenticate()).isTrue();
        assertThatThrownBy(() -> user.activate(OTHER_HASH, CLOCK)).isInstanceOf(UserException.InvalidStatusTransition.class);
    }

    @Test
    void onlyAdministratorsOfTheTargetRoleCanInviteIt() {
        EmailAddress email = new EmailAddress("nuevo@clinica.test");
        FullName name = new FullName("Pedro Pérez");
        Actor doctor = new Actor(UUID.randomUUID(), Role.DOCTOR);

        assertThat(User.invite(email, name, Role.ADMIN, SUPER_ADMIN, CLOCK).role()).isEqualTo(Role.ADMIN);
        assertThat(User.invite(email, name, Role.MEDICAL_RECORDS, ADMIN, CLOCK).role()).isEqualTo(Role.MEDICAL_RECORDS);
        assertThatThrownBy(() -> User.invite(email, name, Role.ADMIN, ADMIN, CLOCK)).isInstanceOf(UserException.RoleNotManageable.class);
        assertThatThrownBy(() -> User.invite(email, name, Role.SUPER_ADMIN, ADMIN, CLOCK)).isInstanceOf(UserException.RoleNotManageable.class);
        assertThatThrownBy(() -> User.invite(email, name, Role.NURSE, doctor, CLOCK)).isInstanceOf(UserException.RoleNotManageable.class);
    }

    @Test
    void suspensionRecordsWhoAndWhyAndRevokesExistingTokens() {
        User user = active(Role.RECEPTIONIST);

        user.suspend("Investigación por acceso indebido", ADMIN, at(LATER));

        assertThat(user.status()).isEqualTo(new UserStatus.Suspended("Investigación por acceso indebido", ADMIN, LATER));
        assertThat(user.mayAuthenticate()).isFalse();
        assertThat(user.tokensNotBefore()).isEqualTo(LATER);

        user.reactivate(ADMIN, at(LATER.plusSeconds(60)));

        assertThat(user.status()).isEqualTo(new UserStatus.Active());
        assertThat(user.tokensNotBefore()).isEqualTo(LATER);
    }

    @Test
    void deactivationIsReversibleAndKeepsTheSameIdentity() {
        User user = active(Role.DOCTOR);
        UUID identity = user.uuid();

        user.deactivate("Retiro de la institución", SUPER_ADMIN, at(LATER));
        assertThat(user.status()).isInstanceOf(UserStatus.Deactivated.class);
        assertThatThrownBy(() -> user.suspend("Suspensión tras el retiro", SUPER_ADMIN, CLOCK))
                .isInstanceOf(UserException.InvalidStatusTransition.class);

        user.reactivate(SUPER_ADMIN, CLOCK);

        assertThat(user.uuid()).isEqualTo(identity);
        assertThat(user.status()).isEqualTo(new UserStatus.Active());
    }

    @Test
    void aUserDeactivatedBeforeActivatingGoesBackToPendingActivation() {
        User user = invited(Role.NURSE);

        user.deactivate("Invitación enviada por error", ADMIN, CLOCK);
        user.reactivate(ADMIN, CLOCK);

        assertThat(user.status()).isEqualTo(new UserStatus.PendingActivation());
    }

    @Test
    void nobodyChangesTheirOwnRoleOrStatus() {
        User admin = active(Role.ADMIN, "admin@clinica.test");
        Actor self = new Actor(admin.uuid(), Role.SUPER_ADMIN);

        assertThatThrownBy(() -> admin.changeRole(Role.SUPER_ADMIN, self, CLOCK)).isInstanceOf(UserException.SelfManagement.class);
        assertThatThrownBy(() -> admin.suspend("Me suspendo a mí mismo", self, CLOCK)).isInstanceOf(UserException.SelfManagement.class);
        assertThatThrownBy(() -> admin.deactivate("Me retiro a mí mismo", self, CLOCK)).isInstanceOf(UserException.SelfManagement.class);

        admin.rename(new FullName("Administradora General"), new Actor(admin.uuid(), Role.ADMIN));
        assertThat(admin.fullName().value()).isEqualTo("Administradora General");
    }

    @Test
    void anAdminCannotPromoteToOrManagePrivilegedRoles() {
        User nurse = active(Role.NURSE);
        User otherAdmin = active(Role.ADMIN, "otro.admin@clinica.test");

        assertThatThrownBy(() -> nurse.changeRole(Role.ADMIN, ADMIN, CLOCK)).isInstanceOf(UserException.RoleNotManageable.class);
        assertThatThrownBy(() -> otherAdmin.suspend("Conflicto entre administradores", ADMIN, CLOCK))
                .isInstanceOf(UserException.RoleNotManageable.class);
        assertThatThrownBy(() -> otherAdmin.rename(new FullName("Otro Nombre"), ADMIN)).isInstanceOf(UserException.RoleNotManageable.class);

        nurse.changeRole(Role.DOCTOR, ADMIN, at(LATER));

        assertThat(nurse.role()).isEqualTo(Role.DOCTOR);
        assertThat(nurse.tokensNotBefore()).isEqualTo(LATER);
    }

    @Test
    void aSuspectedCompromiseForcesAPasswordChangeAndRevokesTokens() {
        User user = active(Role.DOCTOR);

        user.requirePasswordChange("Contraseña expuesta en un correo", ADMIN, at(LATER));

        assertThat(user.credentialState()).isEqualTo(new CredentialState.ChangeRequired(NOW, "Contraseña expuesta en un correo"));
        assertThat(user.tokensNotBefore()).isEqualTo(LATER);
        assertThat(user.mayAuthenticate()).isTrue();

        user.changePassword(OTHER_HASH, at(LATER.plusSeconds(30)));

        assertThat(user.credentialState()).isEqualTo(new CredentialState.Current(LATER.plusSeconds(30)));
        assertThat(user.currentHash()).contains(OTHER_HASH);
    }

    @Test
    void aResetRevokesTokensButAPlainChangeDoesNot() {
        User user = active(Role.NURSE);

        user.changePassword(OTHER_HASH, at(LATER));
        assertThat(user.tokensNotBefore()).isEqualTo(NOW);

        user.resetPassword(HASH, at(LATER));
        assertThat(user.tokensNotBefore()).isEqualTo(LATER);
    }

    @Test
    void passwordsOnlyChangeForActiveUsers() {
        User pending = invited(Role.NURSE);
        User suspended = active(Role.NURSE, "suspendida@clinica.test");
        suspended.suspend("Suspensión preventiva", ADMIN, CLOCK);

        assertThatThrownBy(() -> pending.changePassword(HASH, CLOCK)).isInstanceOf(UserException.NotActive.class);
        assertThatThrownBy(() -> suspended.resetPassword(HASH, CLOCK)).isInstanceOf(UserException.NotActive.class);
        assertThatThrownBy(() -> pending.requirePasswordChange("Sin contraseña todavía", ADMIN, CLOCK))
                .isInstanceOf(UserException.NotActive.class);
    }

    @Test
    void aSecondFactorIsEnrolledOnceAndOnlyAnAdministratorCanResetIt() {
        User user = active(Role.DOCTOR);
        assertThat(user.secondFactorState()).isEqualTo(new SecondFactorState.NotEnrolled());
        assertThatThrownBy(() -> invited(Role.NURSE).enrollTotp(CLOCK)).isInstanceOf(UserException.NotActive.class);

        user.enrollTotp(at(LATER));

        assertThat(user.secondFactorState()).isEqualTo(new SecondFactorState.TotpEnrolled(LATER));
        assertThatThrownBy(() -> user.enrollTotp(CLOCK)).isInstanceOf(UserException.SecondFactorAlreadyEnrolled.class);
        assertThatThrownBy(() -> user.resetSecondFactor("Pérdida del teléfono", new Actor(user.uuid(), Role.SUPER_ADMIN), CLOCK))
                .isInstanceOf(UserException.SelfManagement.class);

        user.resetSecondFactor("Pérdida del teléfono", ADMIN, at(LATER.plusSeconds(60)));

        assertThat(user.secondFactorState()).isEqualTo(new SecondFactorState.NotEnrolled());
        assertThat(user.tokensNotBefore()).isEqualTo(LATER.plusSeconds(60));
        assertThatThrownBy(() -> user.resetSecondFactor("Pérdida del teléfono", ADMIN, CLOCK))
                .isInstanceOf(UserException.SecondFactorNotEnrolled.class);
    }

    @Test
    void statusReasonsMustExplainTheDecision() {
        User user = active(Role.NURSE);

        assertThatThrownBy(() -> user.suspend("corto", ADMIN, CLOCK))
                .isInstanceOf(UserException.InvalidData.class)
                .hasMessageContaining("al menos 10");
        assertThatThrownBy(() -> user.suspend(null, ADMIN, CLOCK)).isInstanceOf(UserException.InvalidData.class);
    }
}
