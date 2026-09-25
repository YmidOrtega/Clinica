package com.ClinicaDeYmid.billing_service.application.sale;

import java.util.UUID;

public interface PractitionerDirectory {

    PractitionerLookup practitioner(UUID practitionerUuid);
}
