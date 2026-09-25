package com.ClinicaDeYmid.billing_service.support;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class AdmissionEvents {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String SERVICE = "0f8e7d6c-5b4a-4c3d-9e2f-1a0b9c8d7e6f";
    private static final String PATIENT = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

    private AdmissionEvents() {
    }

    public static String nextNumber() {
        return "ADM-2026-%06d".formatted(SEQUENCE.incrementAndGet() + (int) (System.nanoTime() % 400000) + 100000);
    }

    public static String registered(UUID admission, String number, long version) {
        return event("AdmissionRegistered", admission, number, version, "INPATIENT", "REGISTERED", "");
    }

    public static String phaseChanged(UUID admission, String number, long version, String kind) {
        return event("AdmissionPhaseChanged", admission, number, version, kind, "ACTIVE",
                ", \"previousServiceUuid\": \"" + UUID.randomUUID() + "\"");
    }

    public static String discharged(UUID admission, String number, long version, String discharge) {
        return event("AdmissionDischarged", admission, number, version, "INPATIENT", "DISCHARGED",
                ", \"discharge\": \"" + discharge + "\"");
    }

    public static String cancelled(UUID admission, String number, long version, String reason) {
        return event("AdmissionCancelled", admission, number, version, "INPATIENT", "CANCELLED",
                ", \"reason\": \"" + reason + "\"");
    }

    private static String event(String type, UUID admission, String number, long version, String kind, String status,
                                String extra) {
        return """
                {"eventId": "%s", "type": "%s", "schemaVersion": 1, "occurredAt": "2026-09-25T15:00:00Z",
                 "admissionUuid": "%s", "admissionVersion": %d,
                 "data": {"admission": {"uuid": "%s", "number": "%s", "patientUuid": "%s", "kind": "%s",
                          "status": "%s", "configurationServiceUuid": "%s", "coverage": "COVERED"}%s}}
                """.formatted(UUID.randomUUID(), type, admission, version, admission, number, PATIENT, kind, status,
                SERVICE, extra);
    }
}
