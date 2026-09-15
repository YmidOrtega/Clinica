package com.ClinicaDeYmid.commons.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Locale;

public class ClinicaJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authoritiesOf(jwt), jwt.getSubject());
    }

    private static List<GrantedAuthority> authoritiesOf(Jwt jwt) {
        if (TokenSubjects.isService(jwt)) {
            return TokenSubjects.scopesOf(jwt).stream()
                    .<GrantedAuthority>map(scope -> new SimpleGrantedAuthority(ClinicaJwtClaims.SCOPE_PREFIX + scope))
                    .toList();
        }
        return TokenSubjects.user(jwt)
                .<List<GrantedAuthority>>map(user -> List.of(new SimpleGrantedAuthority(
                        ClinicaJwtClaims.ROLE_PREFIX + user.role().trim().toUpperCase(Locale.ROOT))))
                .orElse(List.of());
    }
}
