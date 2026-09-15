package com.ClinicaDeYmid.api_gateway.infrastructure.oauth;

import com.ClinicaDeYmid.api_gateway.infrastructure.config.GatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class StaffAccessTokens {

    private static final Logger log = LoggerFactory.getLogger(StaffAccessTokens.class);
    private static final String LOCK_PREFIX = "clinica:gateway:refresh-lock:";
    private static final Duration LOCK_LEASE = Duration.ofSeconds(10);
    private static final Duration WAIT_FOR_OTHER_REFRESH = Duration.ofSeconds(5);
    private static final Duration POLL = Duration.ofMillis(100);
    private static final RedisScript<Long> RELEASE = RedisScript.of(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);

    private final RedisAuthorizedClients clients;
    private final RestClientRefreshTokenTokenResponseClient refreshClient;
    private final StringRedisTemplate redis;
    private final Duration refreshBeforeExpiry;
    private final Clock clock;

    StaffAccessTokens(RedisAuthorizedClients clients, RestClientRefreshTokenTokenResponseClient refreshClient, StringRedisTemplate redis,
                      GatewayProperties properties, Clock clock) {
        this.clients = clients;
        this.refreshClient = refreshClient;
        this.redis = redis;
        this.refreshBeforeExpiry = properties.auth().refreshBeforeExpiry();
        this.clock = clock;
    }

    public String accessToken(HttpServletRequest request) {
        String key = clients.storeKey(request).orElseThrow(() -> new SessionExpiredException("The session has no tokens"));
        RedisAuthorizedClients.StoredClient current = load(key);
        if (fresh(current)) {
            return current.accessToken();
        }
        String lock = LOCK_PREFIX + key;
        String owner = UUID.randomUUID().toString();
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lock, owner, LOCK_LEASE))) {
            try {
                RedisAuthorizedClients.StoredClient latest = load(key);
                return fresh(latest) ? latest.accessToken() : refresh(key, latest);
            } finally {
                redis.execute(RELEASE, List.of(lock), owner);
            }
        }
        return waitForOtherRefresh(key, current);
    }

    private String refresh(String key, RedisAuthorizedClients.StoredClient stored) {
        if (stored.refreshToken() == null) {
            throw expired(key, "The session has no refresh token");
        }
        OAuth2AuthorizedClient client = clients.toClient(AuthServerClientConfiguration.REGISTRATION_ID, stored);
        try {
            OAuth2AccessTokenResponse response = refreshClient.getTokenResponse(new OAuth2RefreshTokenGrantRequest(client.getClientRegistration(),
                    client.getAccessToken(), client.getRefreshToken()));
            OAuth2AuthorizedClient refreshed = new OAuth2AuthorizedClient(client.getClientRegistration(), client.getPrincipalName(),
                    response.getAccessToken(), response.getRefreshToken() == null ? client.getRefreshToken() : response.getRefreshToken());
            clients.save(key, RedisAuthorizedClients.stored(refreshed, stored.idToken()));
            return refreshed.getAccessToken().getTokenValue();
        } catch (OAuth2AuthorizationException rejected) {
            if (OAuth2ErrorCodes.INVALID_GRANT.equals(rejected.getError().getErrorCode())) {
                throw expired(key, "auth-service no longer accepts the refresh token");
            }
            return stillValidOrUnavailable(stored, rejected);
        } catch (RuntimeException unreachable) {
            return stillValidOrUnavailable(stored, unreachable);
        }
    }

    private String waitForOtherRefresh(String key, RedisAuthorizedClients.StoredClient stale) {
        Instant deadline = Instant.now(clock).plus(WAIT_FOR_OTHER_REFRESH);
        while (Instant.now(clock).isBefore(deadline)) {
            sleep();
            RedisAuthorizedClients.StoredClient latest = load(key);
            if (fresh(latest)) {
                return latest.accessToken();
            }
        }
        return stillValidOrUnavailable(stale, null);
    }

    private RedisAuthorizedClients.StoredClient load(String key) {
        return clients.load(key).orElseThrow(() -> new SessionExpiredException("The session tokens are gone"));
    }

    private boolean fresh(RedisAuthorizedClients.StoredClient stored) {
        return stored.accessExpiresAt() != null && stored.accessExpiresAt().minus(refreshBeforeExpiry).isAfter(Instant.now(clock));
    }

    private String stillValidOrUnavailable(RedisAuthorizedClients.StoredClient stored, RuntimeException cause) {
        if (stored.accessExpiresAt() != null && stored.accessExpiresAt().isAfter(Instant.now(clock))) {
            log.warn("Could not refresh the access token yet; using the current one until it expires");
            return stored.accessToken();
        }
        throw new AuthUnavailableException("auth-service could not refresh the access token", cause);
    }

    private SessionExpiredException expired(String key, String reason) {
        clients.delete(key);
        return new SessionExpiredException(reason);
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AuthUnavailableException("Interrupted while waiting for a token refresh", interrupted);
        }
    }
}
