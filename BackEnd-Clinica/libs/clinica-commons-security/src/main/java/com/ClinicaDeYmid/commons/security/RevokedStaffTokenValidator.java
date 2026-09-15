package com.ClinicaDeYmid.commons.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class RevokedStaffTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
            "The staff member was suspended or their sessions were revoked", null);

    private final StaffAccessRegistry registry;

    RevokedStaffTokenValidator(StaffAccessRegistry registry) {
        this.registry = registry;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        return TokenSubjects.user(jwt)
                .filter(user -> jwt.getIssuedAt() != null && registry.revoked(user.uuid(), jwt.getIssuedAt()))
                .map(user -> OAuth2TokenValidatorResult.failure(REVOKED))
                .orElseGet(OAuth2TokenValidatorResult::success);
    }
}
