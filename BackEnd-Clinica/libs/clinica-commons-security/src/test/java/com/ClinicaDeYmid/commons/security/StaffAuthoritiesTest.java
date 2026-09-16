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
