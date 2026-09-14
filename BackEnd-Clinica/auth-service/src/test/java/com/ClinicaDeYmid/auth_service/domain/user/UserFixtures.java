package com.ClinicaDeYmid.auth_service.domain.user;

import com.ClinicaDeYmid.auth_service.domain.password.PasswordHash;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public final class UserFixtures {

    public static final Instant NOW = Instant.parse("2026-09-14T15:00:00.123456Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final Actor SUPER_ADMIN = new Actor(UUID.fromString("00000000-0000-4000-8000-000000000001"), Role.SUPER_ADMIN);
    public static final Actor ADMIN = new Actor(UUID.fromString("00000000-0000-4000-8000-000000000002"), Role.ADMIN);
    public static final PasswordHash HASH = new PasswordHash("$argon2id$v=19$m=19456,t=2,p=1$c2FsdHNhbHRzYWx0c2FsdA$aGFzaGhhc2hoYXNoaGFzaGhhc2hoYXNoaGFzaGhhc2g");
    public static final PasswordHash OTHER_HASH = new PasswordHash("$argon2id$v=19$m=19456,t=2,p=1$b3RoZXJzYWx0b3RoZXI$b3RoZXJoYXNob3RoZXJoYXNob3RoZXJoYXNob3RoZXI");

    private UserFixtures() {
    }

    public static User invited(Role role) {
        return invited(role, "ana.rojas@clinica.test");
    }

    public static User invited(Role role, String email) {
        return User.invite(new EmailAddress(email), new FullName("Ana María Rojas"), role, SUPER_ADMIN, CLOCK);
    }

    public static User active(Role role) {
        return active(role, "ana.rojas@clinica.test");
    }

    public static User active(Role role, String email) {
        User user = invited(role, email);
        user.activate(HASH, CLOCK);
        return user;
    }

    public static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
