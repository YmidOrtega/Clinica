package com.ClinicaDeYmid.admissions_service.application.practitioner;

import com.ClinicaDeYmid.admissions_service.domain.practitioner.PractitionerReference;

public sealed interface PractitionerLookup {

    record Found(PractitionerReference reference) implements PractitionerLookup {
    }

    record NotFound() implements PractitionerLookup {
    }

    record Unavailable() implements PractitionerLookup {
    }
}
