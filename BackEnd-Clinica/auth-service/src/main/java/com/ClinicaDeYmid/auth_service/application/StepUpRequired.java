package com.ClinicaDeYmid.auth_service.application;

import java.time.Duration;

public final class StepUpRequired extends RuntimeException {

    private final Duration maxAge;

    public StepUpRequired(Duration maxAge) {
        super("A second factor verified in the last " + maxAge.toSeconds() + " seconds is required");
        this.maxAge = maxAge;
    }

    public Duration maxAge() {
        return maxAge;
    }
}
