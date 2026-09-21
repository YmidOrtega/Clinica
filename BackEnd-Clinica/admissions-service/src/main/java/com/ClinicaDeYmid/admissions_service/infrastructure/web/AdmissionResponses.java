package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionPhase;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus;
import com.ClinicaDeYmid.admissions_service.domain.Cause;
import com.ClinicaDeYmid.admissions_service.domain.AttendingPractitioner;
import com.ClinicaDeYmid.admissions_service.domain.Coverage;
import com.ClinicaDeYmid.admissions_service.domain.DeathNotice;
import com.ClinicaDeYmid.admissions_service.domain.Discharge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class AdmissionResponses {

    record DischargeView(Discharge.Code type, Instant at, String notes, String signedBy, String signatureDocument,
                         String repsCode, String facility, String reason, Instant noticedAt, Instant occurredAt,
                         String certificateNumber) {

        static DischargeView from(Discharge discharge) {
            return switch (discharge) {
                case Discharge.Medical medical -> new DischargeView(Discharge.Code.MEDICAL, medical.at(),
                        medical.notes(), null, null, null, null, null, null, null, null);
                case Discharge.Voluntary voluntary -> new DischargeView(Discharge.Code.VOLUNTARY, voluntary.at(),
                        null, voluntary.signedBy(), voluntary.signatureDocument(), null, null, null, null, null, null);
                case Discharge.Referral referral -> new DischargeView(Discharge.Code.REFERRAL, referral.at(),
                        null, null, null, referral.repsCode(), referral.facility(), referral.reason(), null, null,
                        null);
                case Discharge.Escape escape -> new DischargeView(Discharge.Code.ESCAPE, escape.at(),
                        null, null, null, null, null, null, escape.noticedAt(), null, null);
                case Discharge.Death death -> new DischargeView(Discharge.Code.DEATH, death.at(),
                        null, null, null, null, null, null, null, death.occurredAt(), death.certificateNumber());
            };
        }
    }

    record DeathNoticeView(DeathNotice.Status status, String detail, Instant at) {

        static DeathNoticeView from(DeathNotice notice) {
            return notice == null ? null : new DeathNoticeView(notice.status(), notice.detail(), notice.at());
        }
    }

    record StatusView(AdmissionStatus.Code code, String reason, Instant since, boolean open,
                      DischargeView discharge) {

        static StatusView from(AdmissionStatus status) {
            return switch (status) {
                case AdmissionStatus.Registered ignored ->
                        new StatusView(AdmissionStatus.Code.REGISTERED, null, null, true, null);
                case AdmissionStatus.Active active ->
                        new StatusView(AdmissionStatus.Code.ACTIVE, null, active.since(), true, null);
                case AdmissionStatus.Discharged discharged ->
                        new StatusView(AdmissionStatus.Code.DISCHARGED, null, discharged.at(), false,
                                DischargeView.from(discharged.discharge()));
                case AdmissionStatus.Cancelled cancelled ->
                        new StatusView(AdmissionStatus.Code.CANCELLED, cancelled.reason(), cancelled.at(), false,
                                null);
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

    record AttendingView(UUID practitionerUuid, String fullName, String registrationNumber) {

        static AttendingView from(AttendingPractitioner attending) {
            return attending == null ? null : new AttendingView(attending.practitionerUuid(), attending.fullName(),
                    attending.registrationNumber());
        }
    }

    record CoverageView(Coverage.Code status, UUID contractUuid, String contractNumber, UUID payerUuid,
                        String detail, Instant checkedAt, boolean pending) {

        static CoverageView from(Coverage coverage) {
            return coverage == null ? null : new CoverageView(coverage.status(), coverage.contractUuid(),
                    coverage.contractNumber(), coverage.payerUuid(), coverage.detail(), coverage.checkedAt(),
                    coverage.pending());
        }
    }

    record AdmissionView(UUID uuid, String number, UUID patientUuid, Cause cause, UUID careTypeUuid,
                         AdmissionKind kind, boolean bedRequired, StatusView status, PhaseView currentPhase,
                         List<PhaseView> phases, CompanionView companion, CoverageView coverage, UUID bedUuid,
                         AttendingView attending, DeathNoticeView deathNotice) {

        static AdmissionView from(Admission admission) {
            return new AdmissionView(admission.uuid(), admission.number(), admission.patientUuid(), admission.cause(),
                    admission.careType() == null ? null : admission.careType().uuid(),
                    admission.kind(), admission.bedRequired(), StatusView.from(admission.status()),
                    PhaseView.from(admission.lastPhase()),
                    admission.phases().stream().map(PhaseView::from).toList(),
                    admission.companion() == null ? null : new CompanionView(admission.companion().fullName(),
                            admission.companion().phoneNumber(), admission.companion().relationship()),
                    CoverageView.from(admission.coverage()), admission.bedUuid(),
                    AttendingView.from(admission.attending()), DeathNoticeView.from(admission.deathNotice()));
        }
    }

    private AdmissionResponses() {
    }
}
