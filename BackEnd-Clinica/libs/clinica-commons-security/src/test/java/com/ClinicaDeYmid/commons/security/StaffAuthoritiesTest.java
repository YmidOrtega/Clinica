package com.ClinicaDeYmid.commons.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class StaffAuthoritiesTest {

    private final ClinicaJwtAuthenticationConverter converter = new ClinicaJwtAuthenticationConverter();

    @ParameterizedTest
    @EnumSource(StaffRole.class)
    void everyRoleResolvesTheSamePermissionsByName(StaffRole role) {
        assertThat(StaffRole.of(role.name())).contains(role);
        assertThat(StaffRole.permissionsOf(role.name())).isEqualTo(role.permissions());
        assertThat(StaffRole.permissionsOf(role.name().toLowerCase())).isEqualTo(role.permissions());
    }

    @ParameterizedTest
    @EnumSource(StaffRole.class)
    void everyRoleReachesTheTokenAsAuthorities(StaffRole role) {
        List<String> authorities = authoritiesOf(staff(role.name()));

        assertThat(authorities).contains("ROLE_" + role.name());
        assertThat(authorities).containsAll(role.permissions().stream().map(StaffPermission::code).toList());
        assertThat(authorities).hasSize(role.permissions().size() + 1);
    }

    @Test
    void theSuperAdminHoldsEveryPermission() {
        assertThat(StaffRole.SUPER_ADMIN.permissions()).isEqualTo(EnumSet.allOf(StaffPermission.class));
    }

    @Test
    void humanResourcesOnlyReachesTheDirectory() {
        assertThat(StaffRole.HUMAN_RESOURCES.permissions()).containsExactlyInAnyOrder(
                StaffPermission.PRACTITIONERS_READ, StaffPermission.PRACTITIONERS_MANAGE,
                StaffPermission.PRACTITIONERS_MANAGE_FEES, StaffPermission.PRACTITIONERS_READ_FEES);
    }

    @Test
    void onlyTheAdministrativeRolesReachTheDirectory() {
        assertThat(EnumSet.allOf(StaffRole.class).stream()
                .filter(role -> role.permissions().contains(StaffPermission.PRACTITIONERS_MANAGE)))
                .containsExactlyInAnyOrder(StaffRole.SUPER_ADMIN, StaffRole.ADMIN, StaffRole.HUMAN_RESOURCES);
    }

    @Test
    void onlyReceptionAdmits() {
        assertThat(rolesHolding(StaffPermission.ADMISSIONS_ADMIT)).containsExactlyInAnyOrder(
                StaffRole.SUPER_ADMIN, StaffRole.ADMIN, StaffRole.RECEPTIONIST);
    }

    @Test
    void receptionReadsTheDirectoryToNameTheAttendingPractitioner() {
        assertThat(StaffRole.RECEPTIONIST.permissions()).contains(StaffPermission.PRACTITIONERS_READ)
                .doesNotContain(StaffPermission.PRACTITIONERS_READ_FEES, StaffPermission.PRACTITIONERS_MANAGE);
    }

    @Test
    void onlyInvoicingAndReceivablesTalkToTheInvoiceAssistant() {
        assertThat(rolesHolding(StaffPermission.ASSISTANT_USE)).containsExactlyInAnyOrder(
                StaffRole.SUPER_ADMIN, StaffRole.ADMIN, StaffRole.BILLING, StaffRole.ACCOUNTS_RECEIVABLE);
    }

    @Test
    void onlyDoctorsDischarge() {
        assertThat(rolesHolding(StaffPermission.ADMISSIONS_DISCHARGE)).containsExactlyInAnyOrder(
                StaffRole.SUPER_ADMIN, StaffRole.ADMIN, StaffRole.DOCTOR);
    }

    @Test
    void onlyNursingMovesPatientsBetweenBeds() {
        assertThat(rolesHolding(StaffPermission.ADMISSIONS_MOVE_BED)).containsExactlyInAnyOrder(
                StaffRole.SUPER_ADMIN, StaffRole.ADMIN, StaffRole.NURSE);
    }

    @Test
    void theIrreversibleAdmissionActionsStayWithAdministration() {
        assertThat(rolesHolding(StaffPermission.ADMISSIONS_CANCEL)).containsExactlyInAnyOrder(
                StaffRole.SUPER_ADMIN, StaffRole.ADMIN);
        assertThat(rolesHolding(StaffPermission.ADMISSIONS_MANAGE_BEDS)).containsExactlyInAnyOrder(
                StaffRole.SUPER_ADMIN, StaffRole.ADMIN);
        assertThat(rolesHolding(StaffPermission.ADMISSIONS_OVERRIDE_COVERAGE)).containsExactlyInAnyOrder(
                StaffRole.SUPER_ADMIN, StaffRole.ADMIN);
    }

    @Test
    void billingReadsAdmissionsWithoutTouchingThem() {
        assertThat(StaffRole.BILLING.permissions()).contains(StaffPermission.ADMISSIONS_READ);
        assertThat(StaffRole.BILLING.permissions()).doesNotContain(
                StaffPermission.ADMISSIONS_ADMIT, StaffPermission.ADMISSIONS_DISCHARGE,
                StaffPermission.ADMISSIONS_CANCEL);
    }

    @Test
    void everyClinicalRoleReadsAdmissions() {
        assertThat(rolesHolding(StaffPermission.ADMISSIONS_READ)).contains(
                StaffRole.DOCTOR, StaffRole.NURSE, StaffRole.RECEPTIONIST, StaffRole.MEDICAL_RECORDS);
    }

    @Test
    void billingRunsTheCycleButOnlyTheAdministrationConfiguresIt() {
        EnumSet.of(StaffPermission.BILLING_SELL, StaffPermission.BILLING_PRICE_MANUALLY,
                        StaffPermission.BILLING_INVOICE, StaffPermission.BILLING_VOID, StaffPermission.BILLING_COLLECT)
                .forEach(permission -> assertThat(rolesHolding(permission)).containsExactlyInAnyOrder(
                        StaffRole.SUPER_ADMIN, StaffRole.ADMIN, StaffRole.BILLING));
        assertThat(rolesHolding(StaffPermission.BILLING_MANAGE_CONFIG))
                .containsExactlyInAnyOrder(StaffRole.SUPER_ADMIN, StaffRole.ADMIN);
    }

    @Test
    void accountsReceivableFilesAndAnswersGlossesButNeverInvoicesNorVoids() {
        EnumSet.of(StaffPermission.BILLING_READ, StaffPermission.BILLING_FILE, StaffPermission.BILLING_GLOSSES)
                .forEach(permission -> assertThat(rolesHolding(permission)).containsExactlyInAnyOrder(
                        StaffRole.SUPER_ADMIN, StaffRole.ADMIN, StaffRole.BILLING, StaffRole.ACCOUNTS_RECEIVABLE));
        assertThat(StaffRole.ACCOUNTS_RECEIVABLE.permissions()).doesNotContain(StaffPermission.BILLING_SELL,
                StaffPermission.BILLING_INVOICE, StaffPermission.BILLING_VOID, StaffPermission.BILLING_COLLECT,
                StaffPermission.BILLING_MANAGE_CONFIG, StaffPermission.BILLING_PRICE_MANUALLY);
        assertThat(StaffRole.ACCOUNTS_RECEIVABLE.permissions()).contains(StaffPermission.ADMISSIONS_READ,
                StaffPermission.CONTRACTING_READ, StaffPermission.PRACTITIONERS_READ);
    }

    @Test
    void billingReadsWhatItPricesWithoutManagingIt() {
        assertThat(StaffRole.BILLING.permissions()).contains(
                StaffPermission.ADMISSIONS_READ, StaffPermission.PRACTITIONERS_READ,
                StaffPermission.CONTRACTING_QUOTE_PRICES);
        assertThat(StaffRole.BILLING.permissions()).doesNotContain(
                StaffPermission.PRACTITIONERS_MANAGE, StaffPermission.PRACTITIONERS_MANAGE_FEES,
                StaffPermission.CONTRACTING_MANAGE_TARIFFS, StaffPermission.CONTRACTING_MANAGE_CONTRACTS);
    }

    @Test
    void billingReadsTheFeesItOwesButNeverAgreesThem() {
        assertThat(rolesHolding(StaffPermission.PRACTITIONERS_READ_FEES)).containsExactlyInAnyOrder(
                StaffRole.SUPER_ADMIN, StaffRole.ADMIN, StaffRole.HUMAN_RESOURCES, StaffRole.BILLING);
        assertThat(rolesHolding(StaffPermission.PRACTITIONERS_MANAGE_FEES)).doesNotContain(StaffRole.BILLING);
    }

    @Test
    void permissionCodesAreUniqueAndNamespaced() {
        assertThat(EnumSet.allOf(StaffPermission.class).stream().map(StaffPermission::code).collect(Collectors.toSet()))
                .hasSize(StaffPermission.values().length)
                .allMatch(code -> code.matches("^[a-z-]+:[a-z-]+$"));
    }

    @Test
    void anUnknownRoleGrantsNoPermission() {
        assertThat(StaffRole.of("CONTRATACION")).isEmpty();
        assertThat(StaffRole.permissionsOf(null)).isEmpty();
        assertThat(authoritiesOf(staff("CONTRATACION"))).containsExactly("ROLE_CONTRATACION");
    }

    @Test
    void aServiceTokenOnlyCarriesItsScopes() {
        Jwt service = Jwt.withTokenValue("token").header("alg", "ES256")
                .subject("contracting-service")
                .claim("client_id", "contracting-service")
                .claim("scope", "openid profile")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(authoritiesOf(service)).containsExactlyInAnyOrder("SCOPE_openid", "SCOPE_profile");
    }

    private List<StaffRole> rolesHolding(StaffPermission permission) {
        return EnumSet.allOf(StaffRole.class).stream()
                .filter(role -> role.permissions().contains(permission))
                .toList();
    }

    private List<String> authoritiesOf(Jwt jwt) {
        return converter.convert(jwt).getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private Jwt staff(String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .claim("role", role)
                .claim("email", "prueba@clinica.test")
                .claim("name", "Prueba")
                .claim("auth_time", now.getEpochSecond())
                .issuedAt(now).expiresAt(now.plusSeconds(300))
                .build();
    }
}
