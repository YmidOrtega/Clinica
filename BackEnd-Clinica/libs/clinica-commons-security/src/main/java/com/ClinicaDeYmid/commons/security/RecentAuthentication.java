package com.ClinicaDeYmid.commons.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class RecentAuthentication {

    private final CurrentUser currentUser;
    private final Duration maxAge;
    private final Clock clock;

    public RecentAuthentication(CurrentUser currentUser, Duration maxAge, Clock clock) {
        this.currentUser = currentUser;
        this.maxAge = maxAge;
        this.clock = clock;
    }

    public AuthenticatedUser require() {
        AuthenticatedUser user = currentUser.get().orElseThrow(() -> new StepUpRequiredException(maxAge));
        if (!user.multiFactor() || user.authenticatedAt().plus(maxAge).isBefore(Instant.now(clock))) {
            throw new StepUpRequiredException(maxAge);
        }
        return user;
    }
}
