package com.ClinicaDeYmid.auth_service.domain.password;

public record NormalizedPassword(String value) {

    @Override
    public String toString() {
        return "NormalizedPassword[redacted]";
    }
}
