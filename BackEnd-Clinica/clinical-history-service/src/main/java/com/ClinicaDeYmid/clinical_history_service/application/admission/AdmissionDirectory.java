package com.ClinicaDeYmid.clinical_history_service.application.admission;

import java.util.UUID;

public interface AdmissionDirectory {

    AdmissionLookup find(UUID admissionUuid);
}
