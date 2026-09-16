package com.ClinicaDeYmid.contracting_service.application;

public interface PatientDirectory {

    PatientLookup findByDocument(String documentType, String documentNumber);
}
