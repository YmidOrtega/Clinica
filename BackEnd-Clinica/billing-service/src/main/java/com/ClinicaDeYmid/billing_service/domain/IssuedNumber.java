package com.ClinicaDeYmid.billing_service.domain;

import java.util.UUID;

public record IssuedNumber(UUID resolutionUuid, String prefix, long consecutive) {

    public String formatted() {
        return prefix + consecutive;
    }
}
