package com.ClinicaDeYmid.clinical_history_service.domain.update;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class RecordUpdatePolicy {

    private static final Set<NoteType> LIST_NOTES = EnumSet.of(NoteType.ADMISSION, NoteType.PROGRESS, NoteType.CONSULTATION,
            NoteType.NURSING, NoteType.DISCHARGE);
    private static final Set<NoteType> VITAL_SIGN_NOTES = EnumSet.of(NoteType.TRIAGE, NoteType.NURSING, NoteType.PROGRESS,
            NoteType.ADMISSION);

    private RecordUpdatePolicy() {
    }

    public static void requireAllowedIn(NoteType type, List<RecordUpdate> updates) {
        for (RecordUpdate update : updates) {
            boolean allowed = switch (update) {
                case RecordUpdate.AddListItem add -> LIST_NOTES.contains(type);
                case RecordUpdate.ChangeListItemStatus change -> LIST_NOTES.contains(type);
                case RecordUpdate.RecordVitalSigns vitals -> VITAL_SIGN_NOTES.contains(type);
            };
            if (!allowed) {
                throw new ClinicalException.InvalidData("updates", "una nota " + type + " no admite " + kindOf(update));
            }
        }
    }

    public static List<AppliedUpdate> apply(List<RecordUpdate> updates, Map<UUID, ListItemState> items, Consumer<RecordUpdate.RecordVitalSigns> checkTime) {
        List<AppliedUpdate> applied = new ArrayList<>();
        for (RecordUpdate update : updates) {
            switch (update) {
                case RecordUpdate.AddListItem add -> applied.add(new AppliedUpdate.ListItemAdded(UUID.randomUUID(), add.details()));
                case RecordUpdate.ChangeListItemStatus change -> {
                    ListItemState item = items.get(change.itemId());
                    if (item == null) {
                        throw new ClinicalException.InvalidData("updates.itemId", "no corresponde a un ítem vigente del paciente");
                    }
                    if (!item.status().canChangeTo(change.status())) {
                        throw new ClinicalException.InvalidData("updates.status",
                                "no se puede pasar de " + item.status() + " a " + change.status());
                    }
                    applied.add(new AppliedUpdate.ListItemStatusChanged(UUID.randomUUID(), item.itemId(), item.category(), change.status(),
                            change.reason()));
                }
                case RecordUpdate.RecordVitalSigns vitals -> {
                    checkTime.accept(vitals);
                    vitals.readings().forEach(reading -> applied.add(
                            new AppliedUpdate.VitalSignObserved(UUID.randomUUID(), reading.kind(), reading.value(), vitals.measuredAt())));
                }
            }
        }
        return List.copyOf(applied);
    }

    private static String kindOf(RecordUpdate update) {
        return switch (update) {
            case RecordUpdate.AddListItem add -> "cambios en listas del paciente";
            case RecordUpdate.ChangeListItemStatus change -> "cambios en listas del paciente";
            case RecordUpdate.RecordVitalSigns vitals -> "signos vitales";
        };
    }
}
