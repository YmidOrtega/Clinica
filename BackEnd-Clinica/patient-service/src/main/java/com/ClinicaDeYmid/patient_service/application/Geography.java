package com.ClinicaDeYmid.patient_service.application;

import java.util.Optional;

public interface Geography {

    Optional<String> departmentOfMunicipality(String municipalityCode);

    Optional<String> countryCode(String alpha2);
}
