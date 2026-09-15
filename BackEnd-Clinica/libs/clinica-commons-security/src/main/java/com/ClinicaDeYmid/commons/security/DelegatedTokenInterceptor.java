package com.ClinicaDeYmid.commons.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;

import java.util.Map;

public class DelegatedTokenInterceptor implements RequestInterceptor {

    private final CurrentUser currentUser;
    private final DelegatedTokens tokens;
    private final Map<String, String> audiences;

    public DelegatedTokenInterceptor(CurrentUser currentUser, DelegatedTokens tokens, Map<String, String> audiences) {
        this.currentUser = currentUser;
        this.tokens = tokens;
        this.audiences = Map.copyOf(audiences);
    }

    @Override
    public void apply(RequestTemplate template) {
        if (template.headers().containsKey(HttpHeaders.AUTHORIZATION) || template.feignTarget() == null) {
            return;
        }
        String audience = audiences.get(template.feignTarget().name());
        if (audience == null || currentUser.get().isEmpty()) {
            return;
        }
        currentUser.bearerToken()
                .ifPresent(subjectToken -> template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.forAudience(subjectToken, audience)));
    }
}
