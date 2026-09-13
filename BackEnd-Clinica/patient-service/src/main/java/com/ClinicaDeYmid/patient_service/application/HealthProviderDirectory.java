package com.ClinicaDeYmid.patient_service.application;

public interface HealthProviderDirectory {

    HealthProviderLookup findByNit(String nit);
}
