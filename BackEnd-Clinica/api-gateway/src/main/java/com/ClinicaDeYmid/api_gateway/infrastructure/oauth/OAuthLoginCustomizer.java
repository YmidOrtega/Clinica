package com.ClinicaDeYmid.api_gateway.infrastructure.oauth;

import com.ClinicaDeYmid.api_gateway.infrastructure.config.GatewayProperties;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

@Component
public class OAuthLoginCustomizer {

    private final ClientRegistrationRepository registrations;
    private final RedisAuthorizedClients clients;
    private final RestClientAuthorizationCodeTokenResponseClient tokenResponseClient;
    private final LoginRedirects redirects;
    private final GatewayProperties properties;

    OAuthLoginCustomizer(ClientRegistrationRepository registrations, RedisAuthorizedClients clients,
                         RestClientAuthorizationCodeTokenResponseClient tokenResponseClient, LoginRedirects redirects, GatewayProperties properties) {
        this.registrations = registrations;
        this.clients = clients;
        this.tokenResponseClient = tokenResponseClient;
        this.redirects = redirects;
        this.properties = properties;
    }

    public void customize(OAuth2LoginConfigurer<HttpSecurity> login) {
        login.loginPage("/bff/login")
                .clientRegistrationRepository(registrations)
                .authorizedClientRepository(clients)
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(
                        new StepUpAuthorizationRequestResolver(registrations, properties.auth().stepUpMaxAge())))
                .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokenResponseClient))
                .successHandler(redirects)
                .failureHandler(redirects);
    }
}
