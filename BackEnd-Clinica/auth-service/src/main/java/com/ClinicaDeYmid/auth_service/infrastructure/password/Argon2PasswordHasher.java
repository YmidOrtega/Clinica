package com.ClinicaDeYmid.auth_service.infrastructure.password;

import com.ClinicaDeYmid.auth_service.domain.password.NormalizedPassword;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordHash;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class Argon2PasswordHasher implements PasswordHasher {

    private final Argon2PasswordEncoder encoder;
    private final PasswordHash decoy;

    Argon2PasswordHasher(Argon2Properties properties) {
        this.encoder = new Argon2PasswordEncoder(properties.saltLength(), properties.hashLength(), properties.parallelism(),
                properties.memoryKib(), properties.iterations());
        this.decoy = new PasswordHash(encoder.encode("decoy-password-that-never-matches"));
    }

    @Override
    public PasswordHash hash(NormalizedPassword password) {
        return new PasswordHash(encoder.encode(password.value()));
    }

    @Override
    public boolean matches(NormalizedPassword password, PasswordHash hash) {
        return encoder.matches(password.value(), hash.value());
    }

    @Override
    public boolean needsRehash(PasswordHash hash) {
        return encoder.upgradeEncoding(hash.value());
    }

    @Override
    public void matchAgainstDecoy(NormalizedPassword password) {
        encoder.matches(password.value(), decoy.value());
    }
}
