package com.ClinicaDeYmid.patient_service.application;

public sealed interface PayerLookup {

    record Found(Payer payer) implements PayerLookup {
    }

    record NotFound() implements PayerLookup {
    }

    record Unavailable() implements PayerLookup {
    }

    record NotAffiliated() implements PayerLookup {
    }
}
