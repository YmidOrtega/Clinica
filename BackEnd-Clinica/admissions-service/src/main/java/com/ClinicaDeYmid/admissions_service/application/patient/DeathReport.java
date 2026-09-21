package com.ClinicaDeYmid.admissions_service.application.patient;

public sealed interface DeathReport {

    record Recorded() implements DeathReport {
    }

    record Failed(String detail) implements DeathReport {
    }
}
