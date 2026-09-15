package com.ClinicaDeYmid.api_gateway.infrastructure.oauth;

import com.ClinicaDeYmid.api_gateway.infrastructure.config.GatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

@Component
public class StaffSessions {

    private final RedisAuthorizedClients clients;
    private final AuthServerRevocation revocation;
    private final GatewayProperties properties;

    StaffSessions(RedisAuthorizedClients clients, AuthServerRevocation revocation, GatewayProperties properties) {
        this.clients = clients;
        this.revocation = revocation;
        this.properties = properties;
    }

    public Optional<String> end(HttpServletRequest request) {
        Optional<RedisAuthorizedClients.StoredClient> stored = clients.storeKey(request).flatMap(clients::load);
        stored.map(RedisAuthorizedClients.StoredClient::refreshToken).ifPresent(revocation::revokeRefreshToken);
        clients.storeKey(request).ifPresent(clients::delete);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return stored.map(RedisAuthorizedClients.StoredClient::idToken).map(idToken -> UriComponentsBuilder
                .fromUriString(properties.auth().publicUrl() + "/connect/logout")
                .queryParam("id_token_hint", idToken)
                .queryParam("post_logout_redirect_uri", properties.frontend().homeUrl())
                .encode()
                .build()
                .toUriString());
    }
}
