package com.ClinicaDeYmid.auth_service.infrastructure.security;

import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

final class StaffTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final Users users;
    private final String audience;

    StaffTokenCustomizer(Users users, String audience) {
        this.users = users;
        this.audience = audience;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        if (!(context.getPrincipal() instanceof StaffAuthentication staff)) {
            return;
        }
        StaffPrincipal principal = staff.getPrincipal();
        User user = users.findByUuid(principal.uuid())
                .filter(User::mayAuthenticate)
                .filter(current -> !current.tokensNotBefore().isAfter(principal.authenticatedAt()))
                .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                        "The session was revoked or the user can no longer sign in", null)));
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        if (!accessToken && !idToken) {
            return;
        }
        context.getClaims().claims(claims -> {
            claims.put("email", user.email().value());
            claims.put("name", user.fullName().value());
            claims.put("role", user.role().name());
            claims.put("auth_time", principal.authenticatedAt().truncatedTo(ChronoUnit.SECONDS));
            claims.put("amr", new ArrayList<>(principal.methods()));
        });
        if (accessToken) {
            context.getClaims().audience(new ArrayList<>(List.of(audience)));
        }
    }
}
