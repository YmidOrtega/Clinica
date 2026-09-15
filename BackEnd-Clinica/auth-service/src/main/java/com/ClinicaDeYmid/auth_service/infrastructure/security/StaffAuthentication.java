package com.ClinicaDeYmid.auth_service.infrastructure.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Objects;

public final class StaffAuthentication extends AbstractAuthenticationToken {

    private final StaffPrincipal principal;

    public StaffAuthentication(StaffPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public StaffPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.uuid().toString();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof StaffAuthentication authentication && principal.equals(authentication.principal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(principal);
    }
}
