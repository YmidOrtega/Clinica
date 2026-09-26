package com.ClinicaDeYmid.billing_service.application.sale;

public sealed interface PractitionerLookup {

    record Found(String fullName, String registrationNumber, boolean attends, String documentType,
                 String documentNumber) implements PractitionerLookup {

        public Found(String fullName, String registrationNumber, boolean attends) {
            this(fullName, registrationNumber, attends, null, null);
        }
    }

    record NotFound() implements PractitionerLookup {
    }

    record Unavailable() implements PractitionerLookup {
    }
}
