package com.ClinicaDeYmid.auth_service.domain.throttle;

import java.time.Duration;
import java.time.Instant;

public record LoginThrottlePolicy(int addressFreeAttempts, Duration addressMaxDelay, int accountFreeAttempts, Duration accountMaxDelay,
                                  int accountLockThreshold) {

    public static final LoginThrottlePolicy DEFAULT = new LoginThrottlePolicy(5, Duration.ofMinutes(15), 10, Duration.ofMinutes(1), 100);

    private static final int MAX_DOUBLINGS = 30;

    public LoginThrottlePolicy {
        if (addressFreeAttempts < 1 || accountFreeAttempts < 1 || accountLockThreshold <= accountFreeAttempts
                || addressMaxDelay.isNegative() || accountMaxDelay.isNegative()) {
            throw new IllegalArgumentException("Invalid login throttle policy");
        }
    }

    public LoginAttemptDecision decide(FailureCount account, FailureCount accountFromAddress, Instant now) {
        if (account.consecutiveFailures() >= accountLockThreshold) {
            return new LoginAttemptDecision.Locked();
        }
        Duration wait = max(remaining(account, accountFreeAttempts, accountMaxDelay, now),
                remaining(accountFromAddress, addressFreeAttempts, addressMaxDelay, now));
        return wait.isZero() ? new LoginAttemptDecision.Allowed() : new LoginAttemptDecision.Delayed(wait);
    }

    private static Duration remaining(FailureCount count, int freeAttempts, Duration maxDelay, Instant now) {
        if (count.consecutiveFailures() < freeAttempts) {
            return Duration.ZERO;
        }
        int doublings = Math.min(count.consecutiveFailures() - freeAttempts, MAX_DOUBLINGS);
        Duration delay = Duration.ofSeconds(1L << doublings);
        if (delay.compareTo(maxDelay) > 0) {
            delay = maxDelay;
        }
        Duration left = Duration.between(now, count.lastFailureAt().plus(delay));
        return left.isNegative() ? Duration.ZERO : left;
    }

    private static Duration max(Duration first, Duration second) {
        return first.compareTo(second) >= 0 ? first : second;
    }
}
