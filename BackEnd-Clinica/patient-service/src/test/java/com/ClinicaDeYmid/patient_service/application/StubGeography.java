package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.patient_service.application.Geography;

import java.util.Map;
import java.util.Optional;

public class StubGeography implements Geography {

    private static final Map<String, String> MUNICIPALITIES = Map.of("68001", "68", "68307", "68", "05001", "05");
    private static final Map<String, String> COUNTRIES = Map.of("CO", "170", "VE", "862");

    @Override
    public Optional<String> departmentOfMunicipality(String municipalityCode) {
        return Optional.ofNullable(MUNICIPALITIES.get(municipalityCode));
    }

    @Override
    public Optional<String> countryCode(String alpha2) {
        return Optional.ofNullable(COUNTRIES.get(alpha2));
    }
}
