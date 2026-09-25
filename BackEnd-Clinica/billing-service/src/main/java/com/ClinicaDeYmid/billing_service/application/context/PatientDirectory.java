package com.ClinicaDeYmid.billing_service.application.context;

import java.util.UUID;

public interface PatientDirectory {

    PatientLookup patient(UUID patientUuid);
}
