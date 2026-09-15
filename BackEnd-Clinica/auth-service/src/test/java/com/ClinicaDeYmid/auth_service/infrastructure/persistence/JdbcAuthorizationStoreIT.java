package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.infrastructure.config.ClockConfiguration;
import com.ClinicaDeYmid.auth_service.infrastructure.security.StaffAuthentication;
import com.ClinicaDeYmid.auth_service.infrastructure.security.StaffPrincipal;
import com.ClinicaDeYmid.auth_service.support.MySqlTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JdbcAuthorizationStore.class, ClockConfiguration.class, MySqlTestContainer.class, JdbcAuthorizationStoreIT.Clients.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JdbcAuthorizationStoreIT {

    private static final RegisteredClient CLIENT = RegisteredClient.withId("api-gateway")
            .clientId("api-gateway")
            .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://gateway.clinica.test/callback")
            .scope("openid")
            .build();

    @TestConfiguration(proxyBeanMethods = false)
    static class Clients {
        @Bean
        RegisteredClientRepository registeredClientRepository() {
            return new InMemoryRegisteredClientRepository(CLIENT);
        }
    }

    @Autowired
    private JdbcAuthorizationStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM auth_sessions.authorizations");
        jdbc.update("DELETE FROM auth_sessions.rotated_refresh_tokens");
    }

    @Test
    void roundTripsAPendingAuthorizationFoundByItsCode() {
        StaffPrincipal principal = principal();
        OAuth2Authorization pending = base(principal)
                .token(new OAuth2AuthorizationCode("code-123", Instant.now(), Instant.now().plusSeconds(60)))
                .attribute(OAuth2AuthorizationRequest.class.getName(), OAuth2AuthorizationRequest.authorizationCode()
                        .authorizationUri("http://auth.clinica.test/oauth2/authorize").clientId("api-gateway")
                        .redirectUri("http://gateway.clinica.test/callback").scopes(Set.of("openid")).state("state-1").build())
                .build();

        store.save(pending);
        OAuth2Authorization found = store.findByToken("code-123", new OAuth2TokenType("code"));

        assertThat(found.getId()).isEqualTo(pending.getId());
        assertThat(found.getToken(OAuth2AuthorizationCode.class).getToken().getTokenValue()).isEqualTo("code-123");
        assertThat(found.<StaffAuthentication>getAttribute(Principal.class.getName()).getPrincipal()).isEqualTo(principal);
        assertThat(found.<OAuth2AuthorizationRequest>getAttribute(OAuth2AuthorizationRequest.class.getName()).getState()).isEqualTo("state-1");
        assertThat(store.findByToken("code-123", OAuth2TokenType.ACCESS_TOKEN)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions.authorization_tokens WHERE value_hash = 'code-123'", Long.class)).isZero();
    }

    @Test
    void keepsAtMostFiveLiveSessionsPerUser() {
        StaffPrincipal principal = principal();
        List<String> ids = IntStream.range(0, 6).mapToObj(session -> {
            OAuth2Authorization authorization = withTokens(base(principal), "session-" + session).build();
            store.save(authorization);
            return authorization.getId();
        }).toList();

        assertThat(store.findById(ids.getFirst())).isNull();
        assertThat(ids.subList(1, 6)).allSatisfy(id -> assertThat(store.findById(id)).isNotNull());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheAuthorization() {
        OAuth2Authorization authorization = withTokens(base(principal()), "first").build();
        store.save(authorization);
        OAuth2Authorization loaded = store.findByToken("refresh-first", OAuth2TokenType.REFRESH_TOKEN);
        store.save(withTokens(OAuth2Authorization.from(loaded), "second").build());

        assertThat(store.findByToken("refresh-second", OAuth2TokenType.REFRESH_TOKEN)).isNotNull();
        assertThat(store.findByToken("refresh-first", OAuth2TokenType.REFRESH_TOKEN)).isNull();
        assertThat(store.findById(authorization.getId())).isNull();
        assertThat(store.findByToken("refresh-second", OAuth2TokenType.REFRESH_TOKEN)).isNull();
    }

    private static OAuth2Authorization.Builder base(StaffPrincipal principal) {
        return OAuth2Authorization.withRegisteredClient(CLIENT)
                .id(UUID.randomUUID().toString())
                .principalName(principal.uuid().toString())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid"))
                .attribute(Principal.class.getName(), new StaffAuthentication(principal));
    }

    private static OAuth2Authorization.Builder withTokens(OAuth2Authorization.Builder builder, String suffix) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return builder
                .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-" + suffix, now, now.plusSeconds(300), Set.of("openid")))
                .refreshToken(new OAuth2RefreshToken("refresh-" + suffix, now, now.plusSeconds(3600)));
    }

    private static StaffPrincipal principal() {
        return new StaffPrincipal(UUID.randomUUID(), "ana@clinica.test", "Ana Rojas", Role.NURSE,
                Instant.parse("2026-09-14T15:00:00.123456Z"), List.of(StaffPrincipal.PASSWORD));
    }
}
