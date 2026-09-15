package com.ClinicaDeYmid.auth_service.infrastructure.security;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class RegisteredClients {

    private RegisteredClients() {
    }

    static InMemoryRegisteredClientRepository from(AuthorizationServerProperties properties) {
        if (properties.clients().isEmpty()) {
            throw new IllegalStateException("clinica.auth.server.clients must register at least one client");
        }
        List<RegisteredClient> clients = properties.clients().entrySet().stream()
                .map(entry -> client(entry.getKey(), entry.getValue(), properties.tokens()))
                .toList();
        return new InMemoryRegisteredClientRepository(clients);
    }

    static Map<String, String> assertionKeys(AuthorizationServerProperties properties) {
        return properties.clients().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().assertionKey()));
    }

    private static RegisteredClient client(String clientId, AuthorizationServerProperties.Client client, AuthorizationServerProperties.Tokens tokens) {
        if (client.assertionKey() == null || client.assertionKey().isBlank()) {
            throw new IllegalStateException("Client " + clientId + " needs an assertion key in transit for private_key_jwt");
        }
        return RegisteredClient.withId(clientId)
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                .authorizationGrantTypes(grants -> client.grantTypes().forEach(grant -> grants.add(new AuthorizationGrantType(grant))))
                .redirectUris(uris -> uris.addAll(client.redirectUris() == null ? List.of() : client.redirectUris()))
                .postLogoutRedirectUris(uris -> uris.addAll(client.postLogoutRedirectUris()))
                .scopes(scopes -> scopes.addAll(client.scopes() == null ? List.of() : client.scopes()))
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .tokenEndpointAuthenticationSigningAlgorithm(SignatureAlgorithm.ES256)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(tokens.accessTokenTtl())
                        .refreshTokenTimeToLive(tokens.refreshTokenTtl())
                        .authorizationCodeTimeToLive(tokens.authorizationCodeTtl())
                        .reuseRefreshTokens(false)
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.ES256)
                        .build())
                .build();
    }
}
