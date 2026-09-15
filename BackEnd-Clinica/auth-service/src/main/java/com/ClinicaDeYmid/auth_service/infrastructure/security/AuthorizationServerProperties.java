package com.ClinicaDeYmid.auth_service.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ConfigurationProperties("clinica.auth.server")
public record AuthorizationServerProperties(String issuer, String loginUrl, String homeUrl, @DefaultValue Tokens tokens,
                                            @DefaultValue("12h") Duration sessionAbsoluteLifetime, Map<String, Client> clients) {

    public AuthorizationServerProperties {
        clients = clients == null ? Map.of() : Map.copyOf(clients);
    }

    public record Tokens(@DefaultValue("5m") Duration accessTokenTtl, @DefaultValue("12h") Duration refreshTokenTtl,
                         @DefaultValue("1m") Duration authorizationCodeTtl, @DefaultValue("5m") Duration exchangedTokenTtl,
                         @DefaultValue("clinica-api") String audience,
                         @DefaultValue("auth-jwt") String signingKey, @DefaultValue("2") int publishedSigningKeyVersions) {
    }

    public record Client(List<String> grantTypes, @DefaultValue List<String> redirectUris, @DefaultValue List<String> postLogoutRedirectUris,
                         @DefaultValue List<String> scopes, String assertionKey, Duration accessTokenTtl,
                         @DefaultValue List<String> exchangeAudiences) {
    }
}
