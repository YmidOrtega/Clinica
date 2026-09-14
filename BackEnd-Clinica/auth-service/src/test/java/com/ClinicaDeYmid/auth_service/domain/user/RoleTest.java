package com.ClinicaDeYmid.auth_service.domain.user;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @ParameterizedTest
    @CsvSource({
            "SUPER_ADMIN, SUPER_ADMIN, true",
            "SUPER_ADMIN, ADMIN, true",
            "SUPER_ADMIN, DOCTOR, true",
            "ADMIN, SUPER_ADMIN, false",
            "ADMIN, ADMIN, false",
            "ADMIN, DOCTOR, true",
            "ADMIN, NURSE, true",
            "ADMIN, RECEPTIONIST, true",
            "ADMIN, MEDICAL_RECORDS, true",
            "DOCTOR, NURSE, false",
            "MEDICAL_RECORDS, RECEPTIONIST, false"
    })
    void onlySuperAdminsManagePrivilegedRolesAndAdminsManageTheRest(Role actor, Role target, boolean manageable) {
        assertThat(target.manageableBy(actor)).isEqualTo(manageable);
    }
}
