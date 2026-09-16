package com.ClinicaDeYmid.patient_service.application;

import java.util.UUID;

public interface PayerDirectory {

    PayerLookup findByUuid(UUID uuid);
}
