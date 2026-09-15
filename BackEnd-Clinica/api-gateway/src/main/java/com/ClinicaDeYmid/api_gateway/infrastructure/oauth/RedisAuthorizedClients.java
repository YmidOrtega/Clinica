package com.ClinicaDeYmid.api_gateway.infrastructure.oauth;

import com.ClinicaDeYmid.api_gateway.infrastructure.config.GatewayProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class RedisAuthorizedClients implements OAuth2AuthorizedClientRepository {

    static final String STORE_KEY = RedisAuthorizedClients.class.getName() + ".STORE_KEY";
    private static final String PREFIX = "clinica:gateway:authorized-client:";

    record StoredClient(String principalName, String accessToken, Instant accessIssuedAt, Instant accessExpiresAt, Set<String> scopes,
                        String refreshToken, Instant refreshIssuedAt, String idToken) {
    }

    private final StringRedisTemplate redis;
    private final ClientRegistrationRepository registrations;
    private final GatewayProperties properties;
    private final ObjectMapper json = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    RedisAuthorizedClients(StringRedisTemplate redis, ClientRegistrationRepository registrations, GatewayProperties properties) {
        this.redis = redis;
        this.registrations = registrations;
        this.properties = properties;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String registrationId, Authentication principal, HttpServletRequest request) {
        return (T) storeKey(request).flatMap(this::load).map(stored -> toClient(registrationId, stored)).orElse(null);
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(true);
        String key = storeKey(request).orElseGet(() -> {
            String created = UUID.randomUUID().toString();
            session.setAttribute(STORE_KEY, created);
            return created;
        });
        String idToken = principal != null && principal.getPrincipal() instanceof OidcUser user
                ? user.getIdToken().getTokenValue()
                : load(key).map(StoredClient::idToken).orElse(null);
        save(key, stored(client, idToken));
    }

    @Override
    public void removeAuthorizedClient(String registrationId, Authentication principal, HttpServletRequest request, HttpServletResponse response) {
        storeKey(request).ifPresent(key -> redis.delete(PREFIX + key));
    }

    Optional<String> storeKey(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? Optional.empty() : Optional.ofNullable((String) session.getAttribute(STORE_KEY));
    }

    Optional<StoredClient> load(String key) {
        String value = redis.opsForValue().get(PREFIX + key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(value, StoredClient.class));
        } catch (JsonProcessingException corrupted) {
            redis.delete(PREFIX + key);
            return Optional.empty();
        }
    }

    void save(String key, StoredClient client) {
        try {
            redis.opsForValue().set(PREFIX + key, json.writeValueAsString(client), properties.session().absoluteLifetime());
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    void delete(String key) {
        redis.delete(PREFIX + key);
    }

    OAuth2AuthorizedClient toClient(String registrationId, StoredClient stored) {
        ClientRegistration registration = registrations.findByRegistrationId(registrationId);
        OAuth2AccessToken access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, stored.accessToken(), stored.accessIssuedAt(),
                stored.accessExpiresAt(), stored.scopes());
        OAuth2RefreshToken refresh = stored.refreshToken() == null ? null : new OAuth2RefreshToken(stored.refreshToken(), stored.refreshIssuedAt());
        return new OAuth2AuthorizedClient(registration, stored.principalName(), access, refresh);
    }

    static StoredClient stored(OAuth2AuthorizedClient client, String idToken) {
        OAuth2RefreshToken refresh = client.getRefreshToken();
        return new StoredClient(client.getPrincipalName(), client.getAccessToken().getTokenValue(), client.getAccessToken().getIssuedAt(),
                client.getAccessToken().getExpiresAt(), client.getAccessToken().getScopes(), refresh == null ? null : refresh.getTokenValue(),
                refresh == null ? null : refresh.getIssuedAt(), idToken);
    }
}
