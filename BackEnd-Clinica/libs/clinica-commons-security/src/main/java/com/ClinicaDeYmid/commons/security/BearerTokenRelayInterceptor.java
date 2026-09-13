package com.ClinicaDeYmid.commons.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;

public class BearerTokenRelayInterceptor implements RequestInterceptor {

    private final CurrentUser currentUser;

    public BearerTokenRelayInterceptor(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
            return;
        }
        currentUser.bearerToken().ifPresent(token -> template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
