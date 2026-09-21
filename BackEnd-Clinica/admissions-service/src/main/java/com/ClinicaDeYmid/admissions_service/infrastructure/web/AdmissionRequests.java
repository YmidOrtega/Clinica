package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionSearch;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus;
import com.ClinicaDeYmid.admissions_service.domain.Cause;
import com.ClinicaDeYmid.admissions_service.domain.Discharge;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

final class AdmissionRequests {

    record CompanionPayload(@NotBlank String fullName, @NotBlank String phoneNumber, String relationship) {
    }

    record Registration(@NotNull UUID patientUuid, @NotNull UUID configurationServiceUuid, @NotNull Cause cause,
                        UUID careTypeUuid, CompanionPayload companion, boolean overrideCoverage) {
    }

    record UnidentifiedRegistration(@NotNull PatientReference.Sex sex,
                                    @NotNull @Min(1900) @Max(2100) Integer estimatedBirthYear,
                                    String description,
                                    @NotNull UUID configurationServiceUuid, @NotNull Cause cause,
                                    UUID careTypeUuid, CompanionPayload companion) {
    }

    record PhaseChange(@NotNull UUID configurationServiceUuid, @NotBlank String reason, UUID bedUuid) {
    }

    record BedAssignment(@NotNull UUID bedUuid) {
    }

    record AttendingPractitioner(@NotNull UUID practitionerUuid) {
    }

    record Reason(@NotBlank String reason) {
    }

    record Document(@NotBlank String type, @NotBlank String number) {
    }

    record Search(Document document, UUID patientUuid, String number, AdmissionStatus.Code status,
                  AdmissionKind kind, UUID configurationServiceUuid, Instant from, Instant to) {

        AdmissionSearch toCriteria() {
            return new AdmissionSearch(patientUuid, number, status, kind, configurationServiceUuid, from, to);
        }
    }

    record DischargePayload(@NotNull Discharge.Code type, String notes, String signedBy, String signatureDocument,
                            String repsCode, String facility, String reason, Instant noticedAt,
                            Instant occurredAt, String certificateNumber) {
    }

    private AdmissionRequests() {
    }
}
