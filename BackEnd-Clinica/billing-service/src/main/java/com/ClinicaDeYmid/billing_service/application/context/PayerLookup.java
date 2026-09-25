package com.ClinicaDeYmid.billing_service.application.context;

public sealed interface PayerLookup {

    record Found(PayerDetails payer) implements PayerLookup {
    }

    record NotFound() implements PayerLookup {
    }

    record Unavailable() implements PayerLookup {
    }
}
