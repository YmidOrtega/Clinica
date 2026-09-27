package com.ClinicaDeYmid.ai_assistant_service.client;

public sealed interface BillingLookup {

    record Found(String json) implements BillingLookup {
    }

    record NotFound() implements BillingLookup {
    }

    record Forbidden() implements BillingLookup {
    }

    record Unavailable() implements BillingLookup {
    }

    record Refused(int status, String problem) implements BillingLookup {
    }
}
