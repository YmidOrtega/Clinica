package com.ClinicaDeYmid.auth_service.application.admin;

import com.ClinicaDeYmid.auth_service.domain.throttle.FailureCount;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginAttemptDecision;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottle;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottlePolicy;
import com.ClinicaDeYmid.auth_service.domain.throttle.ThrottleKey;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserException;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserDirectory {

    public record UserDetails(User user, boolean locked) {
    }

    private final Users users;
    private final UserHistory history;
    private final LoginThrottle throttle;
    private final LoginThrottlePolicy throttlePolicy;
    private final Clock clock;

    public UserDirectory(Users users, UserHistory history, LoginThrottle throttle, LoginThrottlePolicy throttlePolicy, Clock clock) {
        this.users = users;
        this.history = history;
        this.throttle = throttle;
        this.throttlePolicy = throttlePolicy;
        this.clock = clock;
    }

    public UserDetails get(UUID uuid) {
        return details(users.findByUuid(uuid).orElseThrow(UserException.NotFound::new));
    }

    public UserDetails details(User user) {
        FailureCount failures = throttle.failuresOf(new ThrottleKey.Account(user.email()));
        boolean locked = throttlePolicy.decide(failures, FailureCount.NONE, Instant.now(clock)) instanceof LoginAttemptDecision.Locked;
        return new UserDetails(user, locked);
    }

    public Page<User> search(Users.Criteria criteria, Pageable pageable) {
        return users.search(criteria, pageable);
    }

    public List<UserHistory.Revision> history(UUID uuid) {
        users.findByUuid(uuid).orElseThrow(UserException.NotFound::new);
        return history.of(uuid);
    }
}
