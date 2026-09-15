package com.ClinicaDeYmid.api_gateway.infrastructure.oauth;

import com.ClinicaDeYmid.api_gateway.infrastructure.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class AuthServerRevocation {

    private static final Logger log = LoggerFactory.getLogger(AuthServerRevocation.class);

    private final RestClient http = RestClient.create();
    private final ClientAssertions assertions;
    private final String revocationUri;

    AuthServerRevocation(ClientAssertions assertions, GatewayProperties properties) {
        this.assertions = assertions;
        this.revocationUri = properties.auth().internalUrl() + "/oauth2/revoke";
    }

    void revokeRefreshToken(String refreshToken) {
        MultiValueMap<String, String> form = assertions.parameters();
        form.add("client_id", assertions.clientId());
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        try {
            http.post().uri(revocationUri).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
        } catch (RestClientException | IllegalStateException unavailable) {
            log.warn("Could not revoke the refresh token in auth-service; it expires with the session ({})", unavailable.getClass().getSimpleName());
        }
    }
}
