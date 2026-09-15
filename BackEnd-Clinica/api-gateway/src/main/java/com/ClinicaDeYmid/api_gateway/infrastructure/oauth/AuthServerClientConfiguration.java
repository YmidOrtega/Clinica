package com.ClinicaDeYmid.api_gateway.infrastructure.oauth;

import com.ClinicaDeYmid.api_gateway.infrastructure.config.GatewayProperties;
import com.ClinicaDeYmid.commons.openbao.oauth.TransitClientAssertions;
import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;
import com.ClinicaDeYmid.commons.openbao.transit.TransitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

import java.time.Clock;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
class AuthServerClientConfiguration {

    static final String REGISTRATION_ID = "clinica";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(GatewayProperties properties) {
        GatewayProperties.Auth auth = properties.auth();
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(auth.clientId())
                .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile")
                .authorizationUri(auth.publicUrl() + "/oauth2/authorize")
                .tokenUri(auth.internalUrl() + "/oauth2/token")
                .jwkSetUri(auth.internalUrl() + "/oauth2/jwks")
                .issuerUri(auth.publicUrl())
                .userNameAttributeName("sub")
                .providerConfigurationMetadata(Map.of("end_session_endpoint", auth.publicUrl() + "/connect/logout",
                        "revocation_endpoint", auth.internalUrl() + "/oauth2/revoke"))
                .build());
    }

    @Bean
    JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        factory.setJwsAlgorithmResolver(registration -> SignatureAlgorithm.ES256);
        return factory;
    }

    @Bean
    ClientAssertions clientAssertions(TransitClient transit, TransitProperties transitProperties, GatewayProperties properties, Clock clock) {
        TransitClientAssertions assertions = new TransitClientAssertions(
                new TransitKeys(transit, properties.auth().assertionKey(), transitProperties.keyRefreshInterval(), clock), clock);
        return new ClientAssertions(assertions, properties.auth().clientId(), properties.auth().publicUrl());
    }

    @Bean
    RestClientAuthorizationCodeTokenResponseClient authorizationCodeTokenResponseClient(ClientAssertions assertions) {
        RestClientAuthorizationCodeTokenResponseClient client = new RestClientAuthorizationCodeTokenResponseClient();
        client.addParametersConverter(request -> assertions.parameters());
        return client;
    }

    @Bean
    RestClientRefreshTokenTokenResponseClient refreshTokenResponseClient(ClientAssertions assertions) {
        RestClientRefreshTokenTokenResponseClient client = new RestClientRefreshTokenTokenResponseClient();
        client.addParametersConverter(request -> assertions.parameters());
        return client;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
