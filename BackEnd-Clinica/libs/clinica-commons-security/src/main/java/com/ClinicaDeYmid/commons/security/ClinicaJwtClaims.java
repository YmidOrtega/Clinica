package com.ClinicaDeYmid.commons.security;

final class ClinicaJwtClaims {

    static final String EMAIL = "email";
    static final String NAME = "name";
    static final String ROLE = "role";
    static final String AUTH_TIME = "auth_time";
    static final String AMR = "amr";
    static final String CLIENT_ID = "client_id";
    static final String ACTOR = "act";
    static final String SCOPE = "scope";
    static final String MULTI_FACTOR = "mfa";
    static final String ROLE_PREFIX = "ROLE_";
    static final String SCOPE_PREFIX = "SCOPE_";

    private ClinicaJwtClaims() {
    }
}
