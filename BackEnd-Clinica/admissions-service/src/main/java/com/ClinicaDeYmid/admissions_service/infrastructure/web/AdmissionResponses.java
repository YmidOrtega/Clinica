package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionPhase;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus;
import com.ClinicaDeYmid.admissions_service.domain.Cause;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class AdmissionResponses {

    record StatusView(AdmissionStatus.Code code, String reason, Instant since, boolean open) {

        static StatusView from(AdmissionStatus status) {
            return switch (status) {
                case AdmissionStatus.Registered ignored ->
                        new StatusView(AdmissionStatus.Code.REGISTERED, null, null, true);
                case AdmissionStatus.Active active ->
                        new StatusView(AdmissionStatus.Code.ACTIVE, null, active.since(), true);
                case AdmissionStatus.Discharged discharged ->
                        new StatusView(AdmissionStatus.Code.DISCHARGED, null, discharged.at(), false);
                case AdmissionStatus.Cancelled cancelled ->
                        new StatusView(AdmissionStatus.Code.CANCELLED, cancelled.reason(), cancelled.at(), false);
            };
        }
    }

    record PhaseView(UUID uuid, AdmissionKind kind, UUID configurationServiceUuid, String configurationServiceName,
                     Instant startedAt, Instant endedAt, String openingReason, boolean current, boolean bedRequired) {

        static PhaseView from(AdmissionPhase phase) {
            return new PhaseView(phase.uuid(), phase.kind(), phase.configurationService().uuid(),
                    phase.configurationService().name(), phase.startedAt(), phase.endedAt(), phase.openingReason(),
                    phase.current(), phase.bedRequired());
        }
    }

    record CompanionView(String fullName, String phoneNumber, String relationship) {
    }

    record AdmissionView(UUID uuid, String number, UUID patientUuid, Cause cause, UUID careTypeUuid,
                         AdmissionKind kind, boolean bedRequired, StatusView status, PhaseView currentPhase,
                         List<PhaseView> phases, CompanionView companion) {

        static AdmissionView from(Admission admission) {
            return new AdmissionView(admission.uuid(), admission.number(), admission.patientUuid(), admission.cause(),
                    admission.careType() == null ? null : admission.careType().uuid(),
                    admission.kind(), admission.bedRequired(), StatusView.from(admission.status()),
                    PhaseView.from(admission.lastPhase()),
                    admission.phases().stream().map(PhaseView::from).toList(),
                    admission.companion() == null ? null : new CompanionView(admission.companion().fullName(),
                            admission.companion().phoneNumber(), admission.companion().relationship()));
        }
    }

    private AdmissionResponses() {
    }
}
