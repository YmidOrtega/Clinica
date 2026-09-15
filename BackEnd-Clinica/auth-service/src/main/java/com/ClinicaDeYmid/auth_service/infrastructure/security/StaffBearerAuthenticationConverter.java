package com.ClinicaDeYmid.auth_service.infrastructure.security;

import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class StaffBearerAuthenticationConverter implements Converter<Jwt, StaffAuthentication> {

    private final Users users;

    StaffBearerAuthenticationConverter(Users users) {
        this.users = users;
    }

    @Override
    public StaffAuthentication convert(Jwt jwt) {
        Instant issuedAt = jwt.getIssuedAt();
        Instant authenticatedAt = jwt.getClaimAsInstant("auth_time");
        if (issuedAt == null || authenticatedAt == null) {
            throw revoked();
        }
        User user = subject(jwt)
                .flatMap(users::findByUuid)
                .filter(User::mayAuthenticate)
                .filter(current -> !current.tokensNotBefore().truncatedTo(ChronoUnit.SECONDS).isAfter(issuedAt))
                .orElseThrow(StaffBearerAuthenticationConverter::revoked);
        List<String> methods = Optional.ofNullable(jwt.getClaimAsStringList("amr")).orElse(List.of());
        return new StaffAuthentication(new StaffPrincipal(user.uuid(), user.email().value(), user.fullName().value(), user.role(),
                authenticatedAt, methods, authenticatedAt));
    }

    private static Optional<UUID> subject(Jwt jwt) {
        try {
            return Optional.of(UUID.fromString(jwt.getSubject()));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return Optional.empty();
        }
    }

    private static InvalidBearerTokenException revoked() {
        return new InvalidBearerTokenException("The token was revoked or does not belong to an active staff user");
    }
}
