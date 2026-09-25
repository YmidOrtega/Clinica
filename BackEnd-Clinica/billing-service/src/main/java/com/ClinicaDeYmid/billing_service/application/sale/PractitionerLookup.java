package com.ClinicaDeYmid.billing_service.application.sale;

public sealed interface PractitionerLookup {

    record Found(String fullName, String registrationNumber, boolean attends) implements PractitionerLookup {
    }

    record NotFound() implements PractitionerLookup {
    }

    record Unavailable() implements PractitionerLookup {
    }
}
