package com.ClinicaDeYmid.patient_service.application;

public sealed interface HealthProviderLookup {

    record Found(HealthProvider provider) implements HealthProviderLookup {
    }

    record NotFound() implements HealthProviderLookup {
    }

    record Unavailable() implements HealthProviderLookup {
    }

    record NotAffiliated() implements HealthProviderLookup {
    }
}
