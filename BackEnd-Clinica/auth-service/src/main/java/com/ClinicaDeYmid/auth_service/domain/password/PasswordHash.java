package com.ClinicaDeYmid.auth_service.domain.password;

public record PasswordHash(String value) {

    public static final String ARGON2ID_PREFIX = "$argon2id$";
    public static final int MAX_LENGTH = 255;

    public PasswordHash {
        if (value == null || !value.startsWith(ARGON2ID_PREFIX) || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("A password hash must be an Argon2id encoding");
        }
    }

    @Override
    public String toString() {
        return "PasswordHash[argon2id]";
    }
}
