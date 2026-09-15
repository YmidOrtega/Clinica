package com.ClinicaDeYmid.commons.security;

import java.time.Duration;

public class StepUpRequiredException extends RuntimeException {

    private final Duration maxAge;

    public StepUpRequiredException(Duration maxAge) {
        super("A second factor verified in the last " + maxAge.toSeconds() + " seconds is required");
        this.maxAge = maxAge;
    }

    public Duration maxAge() {
        return maxAge;
    }
}
