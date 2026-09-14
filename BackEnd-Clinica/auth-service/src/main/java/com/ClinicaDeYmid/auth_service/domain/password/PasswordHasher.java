package com.ClinicaDeYmid.auth_service.domain.password;

public interface PasswordHasher {

    PasswordHash hash(NormalizedPassword password);

    boolean matches(NormalizedPassword password, PasswordHash hash);

    boolean needsRehash(PasswordHash hash);

    void matchAgainstDecoy(NormalizedPassword password);
}
