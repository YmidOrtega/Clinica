package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.application.record.ClinicalRecordQueries.EncounterRecord;
import com.ClinicaDeYmid.clinical_history_service.application.record.ClinicalRecordQueries.NoteEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterStatus;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDraft;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteRestriction;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.domain.update.AppliedUpdate;
import com.ClinicaDeYmid.clinical_history_service.domain.update.RecordUpdate;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class ClinicalResponses {

    private ClinicalResponses() {
    }

    static String restrictionOf(NoteRestriction restriction) {
        return restriction == null ? null : restriction.name();
    }

    record ClinicianView(UUID uuid, String role) {
        static ClinicianView from(Clinician clinician) {
            return new ClinicianView(clinician.uuid(), clinician.role().name());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EncounterStatusView(String code, Instant closedAt, ClinicianView closedBy) {
        static EncounterStatusView from(EncounterStatus status) {
            return switch (status) {
                case EncounterStatus.Open open -> new EncounterStatusView("OPEN", null, null);
                case EncounterStatus.Closed closed -> new EncounterStatusView("CLOSED", closed.closedAt(), ClinicianView.from(closed.closedBy()));
            };
        }
    }

    record EncounterView(UUID id, UUID patientUuid, String type, String admissionId, Instant openedAt, ClinicianView openedBy,
                         EncounterStatusView status) {
        static EncounterView from(Encounter encounter) {
            return new EncounterView(encounter.id(), encounter.patientUuid(), encounter.type().name(), encounter.admissionId(),
                    encounter.openedAt(), ClinicianView.from(encounter.openedBy()), EncounterStatusView.from(encounter.status()));
        }
    }

    record VoidView(String reason, ClinicianView voidedBy, Instant voidedAt) {
        static VoidView from(Optional<NoteVoid> voiding) {
            return voiding.map(value -> new VoidView(value.reason(), ClinicianView.from(value.voidedBy()), value.voidedAt())).orElse(null);
        }

        static VoidView from(NoteVoid voiding) {
            return from(Optional.of(voiding));
        }
    }

    record NoteSummaryView(UUID id, String type, String restriction, ClinicianView author, Instant occurredAt, Instant recordedAt,
                           boolean extemporaneous, UUID amendsNoteId, VoidView voided) {
        static NoteSummaryView from(NoteEntry entry) {
            SignedNote note = entry.note();
            return new NoteSummaryView(note.id(), note.type().name(), restrictionOf(note.restriction()), ClinicianView.from(note.author()),
                    note.occurredAt(),
                    note.recordedAt(), note.extemporaneous(), note.amends().orElse(null), VoidView.from(entry.voiding()));
        }
    }

    record EncounterRecordView(EncounterView encounter, List<NoteSummaryView> notes) {
        static EncounterRecordView from(EncounterRecord record) {
            return new EncounterRecordView(EncounterView.from(record.encounter()),
                    record.notes().stream().map(NoteSummaryView::from).toList());
        }
    }

    record SignerView(UUID uuid, String role, String email) {
        static SignerView from(SignedNote note) {
            return new SignerView(note.author().uuid(), note.author().role().name(), note.signerEmail());
        }
    }

    record NoteView(UUID id, UUID encounterId, String type, String restriction, NoteContent content, List<AppliedUpdate> updates,
                    SignerView author, Instant occurredAt, Instant recordedAt, boolean extemporaneous, VoidView voided) {
        static NoteView from(NoteEntry entry) {
            SignedNote note = entry.note();
            return new NoteView(note.id(), note.encounterId(), note.type().name(), restrictionOf(note.restriction()), note.content(),
                    note.updates(), SignerView.from(note),
                    note.occurredAt(), note.recordedAt(), note.extemporaneous(), VoidView.from(entry.voiding()));
        }

        static NoteView from(SignedNote note) {
            return from(new NoteEntry(note, Optional.empty()));
        }
    }

    record DraftView(UUID id, UUID encounterId, String type, String restriction, NoteContent content, List<RecordUpdate> updates,
                     Instant occurredAt, long version, Instant createdAt, Instant updatedAt) {
        static DraftView from(NoteDraft draft) {
            return new DraftView(draft.id(), draft.encounterId(), draft.type().name(), restrictionOf(draft.restriction()), draft.content(),
                    draft.updates(), draft.occurredAt(),
                    draft.version(), draft.createdAt(), draft.updatedAt());
        }
    }
}
