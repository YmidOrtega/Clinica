package com.ClinicaDeYmid.api_gateway.infrastructure.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.time.Duration;

class StepUpAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final Duration maxAge;

    StepUpAuthorizationRequestResolver(ClientRegistrationRepository registrations, Duration maxAge) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        this.delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        this.maxAge = maxAge;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return withMaxAge(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return withMaxAge(request, delegate.resolve(request, registrationId));
    }

    private OAuth2AuthorizationRequest withMaxAge(HttpServletRequest request, OAuth2AuthorizationRequest authorization) {
        HttpSession session = request.getSession(false);
        if (authorization == null || session == null || session.getAttribute(LoginRedirects.STEP_UP) == null) {
            return authorization;
        }
        return OAuth2AuthorizationRequest.from(authorization)
                .additionalParameters(parameters -> parameters.put("max_age", Long.toString(maxAge.toSeconds())))
                .build();
    }
}
