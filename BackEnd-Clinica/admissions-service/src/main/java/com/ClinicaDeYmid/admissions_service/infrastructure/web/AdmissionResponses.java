package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionPhase;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus;
import com.ClinicaDeYmid.admissions_service.domain.Cause;
import com.ClinicaDeYmid.admissions_service.domain.AttendingPractitioner;
import com.ClinicaDeYmid.admissions_service.application.CensusEntry;
import com.ClinicaDeYmid.admissions_service.domain.Bed;
import com.ClinicaDeYmid.admissions_service.domain.BedStatus;
import com.ClinicaDeYmid.admissions_service.domain.Coverage;
import com.ClinicaDeYmid.admissions_service.domain.DeathNotice;
import com.ClinicaDeYmid.admissions_service.domain.Discharge;
import com.ClinicaDeYmid.admissions_service.domain.Triage;
import com.ClinicaDeYmid.admissions_service.domain.Discharge;
import com.ClinicaDeYmid.admissions_service.domain.Triage;

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

    record TriageView(Triage.Level level, Instant at, UUID byUuid) {

        static TriageView from(Triage triage) {
            return triage == null ? null : new TriageView(triage.level(), triage.at(), triage.byUuid());
        }
    }

    record AdmissionSummaryView(UUID uuid, String number, UUID patientUuid, AdmissionKind kind,
                               AdmissionStatus.Code status, UUID configurationServiceUuid,
                               String configurationServiceName, Instant startedAt, UUID bedUuid,
                               Coverage.Code coverage, Discharge.Code discharge, TriageView triage) {

        static AdmissionSummaryView from(Admission admission) {
            AdmissionPhase phase = admission.lastPhase();
            return new AdmissionSummaryView(admission.uuid(), admission.number(), admission.patientUuid(),
                    admission.kind(), admission.status().code(), phase.configurationService().uuid(),
                    phase.configurationService().name(), phase.startedAt(), admission.bedUuid(),
                    admission.coverage() == null ? null : admission.coverage().status(),
                    admission.status() instanceof AdmissionStatus.Discharged discharged
                            ? discharged.discharge().code() : null,
                    TriageView.from(admission.triage()));
        }
    }

    record CensusEntryView(UUID bedUuid, String label, UUID roomUuid, String roomName, BedStatus.Code status,
                           Instant since, AdmissionSummaryView occupant) {

        static CensusEntryView from(CensusEntry entry) {
            Bed bed = entry.bed();
            return new CensusEntryView(bed.uuid(), bed.label(), bed.room().uuid(), bed.room().name(),
                    bed.status().code(), sinceOf(bed.status()),
                    entry.occupant() == null ? null : AdmissionSummaryView.from(entry.occupant()));
        }

        private static Instant sinceOf(BedStatus status) {
            return switch (status) {
                case BedStatus.Available ignored -> null;
                case BedStatus.Occupied occupied -> occupied.since();
                case BedStatus.Cleaning cleaning -> cleaning.since();
                case BedStatus.Maintenance maintenance -> maintenance.since();
                case BedStatus.Blocked blocked -> blocked.since();
            };
        }
    }

    record AdmissionView(UUID uuid, String number, UUID patientUuid, Cause cause, UUID careTypeUuid,
                         AdmissionKind kind, boolean bedRequired, StatusView status, PhaseView currentPhase,
                         List<PhaseView> phases, CompanionView companion, CoverageView coverage, UUID bedUuid,
                         AttendingView attending, DeathNoticeView deathNotice, TriageView triage) {

        static AdmissionView from(Admission admission) {
            return new AdmissionView(admission.uuid(), admission.number(), admission.patientUuid(), admission.cause(),
                    admission.careType() == null ? null : admission.careType().uuid(),
                    admission.kind(), admission.bedRequired(), StatusView.from(admission.status()),
                    PhaseView.from(admission.lastPhase()),
                    admission.phases().stream().map(PhaseView::from).toList(),
                    admission.companion() == null ? null : new CompanionView(admission.companion().fullName(),
                            admission.companion().phoneNumber(), admission.companion().relationship()),
                    CoverageView.from(admission.coverage()), admission.bedUuid(),
                    AttendingView.from(admission.attending()), DeathNoticeView.from(admission.deathNotice()),
                    TriageView.from(admission.triage()));
        }
    }

    private AdmissionResponses() {
    }
}
