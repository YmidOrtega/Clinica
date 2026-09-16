package com.ClinicaDeYmid.auth_service.domain.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    void theSuperAdminManagesEveryRole(Role role) {
        assertThat(role.manageableBy(Role.SUPER_ADMIN)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void theAdminManagesEveryRoleButThePrivilegedOnes(Role role) {
        assertThat(role.manageableBy(Role.ADMIN)).isEqualTo(!role.privileged());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"SUPER_ADMIN", "ADMIN"}, mode = EnumSource.Mode.EXCLUDE)
    void anOperationalRoleManagesNobody(Role actor) {
        assertThat(Arrays.stream(Role.values()).noneMatch(role -> role.manageableBy(actor))).isTrue();
    }

    @Test
    void onlyTheAdministrativeRolesArePrivileged() {
        assertThat(Arrays.stream(Role.values()).filter(Role::privileged))
                .containsExactlyInAnyOrder(Role.SUPER_ADMIN, Role.ADMIN);
    }
}
