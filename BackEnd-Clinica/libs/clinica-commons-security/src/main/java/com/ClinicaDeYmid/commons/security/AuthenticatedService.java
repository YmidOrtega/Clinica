package com.ClinicaDeYmid.commons.security;

import java.util.Set;

public record AuthenticatedService(String clientId, Set<String> scopes) {

    public AuthenticatedService {
        scopes = Set.copyOf(scopes);
    }
}
