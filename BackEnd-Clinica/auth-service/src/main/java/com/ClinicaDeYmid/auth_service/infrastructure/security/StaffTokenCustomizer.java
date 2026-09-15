package com.ClinicaDeYmid.auth_service.infrastructure.security;

import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenExchangeActor;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenExchangeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenExchangeCompositeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StaffTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    static final String CLIENT_ID = "client_id";
    static final String ACTOR = "act";

    private static final String INVALID_TARGET = "invalid_target";

    private final Users users;
    private final String audience;
    private final Duration exchangedTokenTtl;
    private final Map<String, Set<String>> exchangeAudiences;

    StaffTokenCustomizer(Users users, String audience, Duration exchangedTokenTtl, Map<String, Set<String>> exchangeAudiences) {
        this.users = users;
        this.audience = audience;
        this.exchangedTokenTtl = exchangedTokenTtl;
        this.exchangeAudiences = Map.copyOf(exchangeAudiences);
    }

    @Override
    public void customize(JwtEncodingContext context) {
        Authentication principal = context.getPrincipal();
        if (AuthorizationGrantType.TOKEN_EXCHANGE.equals(context.getAuthorizationGrantType())) {
            if (!(principal instanceof OAuth2TokenExchangeCompositeAuthenticationToken composite)
                    || !(composite.getSubject() instanceof StaffAuthentication staff)) {
                throw failure(OAuth2ErrorCodes.INVALID_REQUEST, "Token exchange needs the actor token of the calling service");
            }
            exchangedToken(context, staff, composite.getActors());
            return;
        }
        switch (principal) {
            case StaffAuthentication staff -> staffToken(context, staff);
            case OAuth2ClientAuthenticationToken client -> serviceToken(context, client);
            default -> {
            }
        }
    }

    private void staffToken(JwtEncodingContext context, StaffAuthentication staff) {
        User user = activeUser(staff.getPrincipal());
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        if (!accessToken && !OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            return;
        }
        addStaffClaims(context, user, staff.getPrincipal());
        if (accessToken) {
            context.getClaims().audience(new ArrayList<>(List.of(audience)));
        }
    }

    private void exchangedToken(JwtEncodingContext context, StaffAuthentication staff, List<OAuth2TokenExchangeActor> actors) {
        User user = activeUser(staff.getPrincipal());
        OAuth2TokenExchangeAuthenticationToken grant = context.getAuthorizationGrant();
        String clientId = context.getRegisteredClient().getClientId();
        Set<String> requested = grant.getAudiences();
        Set<String> allowed = exchangeAudiences.getOrDefault(clientId, Set.of());
        if (requested.isEmpty() || !allowed.containsAll(requested)) {
            throw failure(INVALID_TARGET, "The client may not obtain tokens for the requested audience");
        }
        addStaffClaims(context, user, staff.getPrincipal());
        Instant expiresAt = context.getClaims().build().getIssuedAt().plus(exchangedTokenTtl);
        OAuth2Authorization.Token<OAuth2Token> subjectToken = context.getAuthorization().getToken(grant.getSubjectToken());
        if (subjectToken != null && subjectToken.getToken().getExpiresAt() != null && subjectToken.getToken().getExpiresAt().isBefore(expiresAt)) {
            expiresAt = subjectToken.getToken().getExpiresAt();
        }
        context.getClaims()
                .subject(staff.getName())
                .audience(new ArrayList<>(requested))
                .expiresAt(expiresAt)
                .claims(claims -> {
                    claims.put(CLIENT_ID, clientId);
                    claims.put(ACTOR, actorChain(actors));
                });
    }

    private void serviceToken(JwtEncodingContext context, OAuth2ClientAuthenticationToken client) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        context.getClaims()
                .audience(new ArrayList<>(List.of(audience)))
                .claim(CLIENT_ID, client.getRegisteredClient().getClientId());
    }

    private User activeUser(StaffPrincipal principal) {
        return users.findByUuid(principal.uuid())
                .filter(User::mayAuthenticate)
                .filter(current -> !current.tokensNotBefore().isAfter(principal.authenticatedAt()))
                .orElseThrow(() -> failure(OAuth2ErrorCodes.INVALID_GRANT, "The session was revoked or the user can no longer sign in"));
    }

    private static void addStaffClaims(JwtEncodingContext context, User user, StaffPrincipal principal) {
        context.getClaims().claims(claims -> {
            claims.put("email", user.email().value());
            claims.put("name", user.fullName().value());
            claims.put("role", user.role().name());
            claims.put("auth_time", principal.authenticatedAt().truncatedTo(ChronoUnit.SECONDS));
            claims.put("amr", new ArrayList<>(principal.methods()));
            if (principal.multiFactor()) {
                claims.put("acr", StaffPrincipal.ACR_MULTI_FACTOR);
            }
        });
    }

    private static Map<String, Object> actorChain(List<OAuth2TokenExchangeActor> actors) {
        Map<String, Object> chain = null;
        for (int index = actors.size() - 1; index >= 0; index--) {
            Map<String, Object> actor = new HashMap<>();
            actor.put("sub", actors.get(index).getSubject());
            actor.put("iss", actors.get(index).getIssuer());
            if (chain != null) {
                actor.put(ACTOR, chain);
            }
            chain = actor;
        }
        return chain;
    }

    private static OAuth2AuthenticationException failure(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }
}
