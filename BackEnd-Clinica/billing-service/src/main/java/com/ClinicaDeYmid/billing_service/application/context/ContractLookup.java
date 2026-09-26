package com.ClinicaDeYmid.billing_service.application.context;

public sealed interface ContractLookup {

    record Found(ContractTerms terms) implements ContractLookup {
    }

    record NotFound() implements ContractLookup {
    }

    record Unavailable() implements ContractLookup {
    }
}
