package com.ClinicaDeYmid.clinical_history_service.domain.update;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.doctor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordUpdatePolicyTest {

    private static final Instant AT = Instant.parse("2026-09-14T15:00:00Z");
    private static final UUID PATIENT = UUID.randomUUID();

    private final RecordUpdate.AddListItem penicillin = new RecordUpdate.AddListItem(
            new ListItemDetails.Allergy("Penicilina", "Urticaria generalizada", ListItemDetails.Allergy.Severity.SEVERE));
    private final RecordUpdate.RecordVitalSigns vitals = new RecordUpdate.RecordVitalSigns(AT, List.of(
            new RecordUpdate.RecordVitalSigns.Reading(VitalSignKind.SYSTOLIC_BLOOD_PRESSURE, new BigDecimal("120")),
            new RecordUpdate.RecordVitalSigns.Reading(VitalSignKind.DIASTOLIC_BLOOD_PRESSURE, new BigDecimal("80.0")),
            new RecordUpdate.RecordVitalSigns.Reading(VitalSignKind.TEMPERATURE, new BigDecimal("37.2"))));

    @Test
    void eachNoteTypeCarriesOnlyTheUpdatesThatBelongToIt() {
        RecordUpdatePolicy.requireAllowedIn(NoteType.ADMISSION, List.of(penicillin, vitals));
        RecordUpdatePolicy.requireAllowedIn(NoteType.TRIAGE, List.of(vitals));

        assertThatThrownBy(() -> RecordUpdatePolicy.requireAllowedIn(NoteType.TRIAGE, List.of(penicillin)))
                .hasMessageContaining("TRIAGE no admite cambios en listas");
        assertThatThrownBy(() -> RecordUpdatePolicy.requireAllowedIn(NoteType.DISCHARGE, List.of(vitals)))
                .hasMessageContaining("signos vitales");
        assertThatThrownBy(() -> RecordUpdatePolicy.requireAllowedIn(NoteType.ADDENDUM, List.of(penicillin)))
                .isInstanceOf(ClinicalException.InvalidData.class);
    }

    @Test
    void vitalSignsRespectPhysiologicalRangesAndPrecision() {
        assertThat(vitals.readings()).extracting(RecordUpdate.RecordVitalSigns.Reading::value)
                .containsExactly(new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("37.2"));
        assertThatThrownBy(() -> VitalSignKind.OXYGEN_SATURATION.validate(new BigDecimal("101")))
                .hasMessageContaining("entre 50 y 100 %");
        assertThatThrownBy(() -> VitalSignKind.HEART_RATE.validate(new BigDecimal("80.5")))
                .hasMessageContaining("máximo 0 decimales");
        assertThatThrownBy(() -> new RecordUpdate.RecordVitalSigns(AT, List.of(
                new RecordUpdate.RecordVitalSigns.Reading(VitalSignKind.SYSTOLIC_BLOOD_PRESSURE, new BigDecimal("80")),
                new RecordUpdate.RecordVitalSigns.Reading(VitalSignKind.DIASTOLIC_BLOOD_PRESSURE, new BigDecimal("90")))))
                .hasMessageContaining("diastólica debe ser menor");
        assertThatThrownBy(() -> new RecordUpdate.RecordVitalSigns(AT, List.of(
                new RecordUpdate.RecordVitalSigns.Reading(VitalSignKind.WEIGHT, new BigDecimal("70")),
                new RecordUpdate.RecordVitalSigns.Reading(VitalSignKind.WEIGHT, new BigDecimal("71")))))
                .hasMessageContaining("repite");
    }

    @Test
    void appliesUpdatesAgainstTheCurrentStateOfTheLists() {
        UUID asthma = UUID.randomUUID();
        UUID oldAllergy = UUID.randomUUID();
        Map<UUID, ListItemState> items = Map.of(
                asthma, new ListItemState(asthma, PATIENT, ListCategory.CHRONIC_CONDITION, ListItemStatus.ACTIVE),
                oldAllergy, new ListItemState(oldAllergy, PATIENT, ListCategory.ALLERGY, ListItemStatus.ENTERED_IN_ERROR));

        List<AppliedUpdate> applied = RecordUpdatePolicy.apply(List.of(penicillin,
                new RecordUpdate.ChangeListItemStatus(asthma, ListItemStatus.RESOLVED, "Sin crisis en cinco años"), vitals), items, taken -> {
        });

        assertThat(applied).hasSize(5);
        assertThat(applied.get(1)).isInstanceOfSatisfying(AppliedUpdate.ListItemStatusChanged.class, changed -> {
            assertThat(changed.itemId()).isEqualTo(asthma);
            assertThat(changed.category()).isEqualTo(ListCategory.CHRONIC_CONDITION);
        });
        assertThatThrownBy(() -> RecordUpdatePolicy.apply(List.of(new RecordUpdate.ChangeListItemStatus(oldAllergy, ListItemStatus.ACTIVE,
                "Reactivar")), items, taken -> {
        })).hasMessageContaining("no se puede pasar de ENTERED_IN_ERROR");
        assertThatThrownBy(() -> RecordUpdatePolicy.apply(List.of(new RecordUpdate.ChangeListItemStatus(UUID.randomUUID(), ListItemStatus.INACTIVE,
                "Otro paciente")), items, taken -> {
        })).hasMessageContaining("no corresponde a un ítem vigente");
        assertThatThrownBy(() -> RecordUpdate.normalize(List.of(new RecordUpdate.ChangeListItemStatus(asthma, ListItemStatus.RESOLVED, "a"),
                new RecordUpdate.ChangeListItemStatus(asthma, ListItemStatus.INACTIVE, "b")))).hasMessageContaining("más de una vez");
    }

    @Test
    void itemsAddedByVoidedNotesCountAsEnteredInError() {
        Clinician author = doctor();
        UUID item = UUID.randomUUID();
        NoteOrigin added = new NoteOrigin(UUID.randomUUID(), UUID.randomUUID(), author, null, AT, false);
        NoteOrigin voidedChange = new NoteOrigin(UUID.randomUUID(), UUID.randomUUID(), author, null, AT.plusSeconds(60), true);
        ListItemDetails details = new ListItemDetails.CurrentMedication("Losartán", "50 mg", "Oral", "Cada 12 horas");

        ListItemHistory resolvedByVoidedNote = new ListItemHistory(item, PATIENT, details, List.of(
                new ListItemHistory.Event(item, ListItemStatus.ACTIVE, null, added),
                new ListItemHistory.Event(UUID.randomUUID(), ListItemStatus.INACTIVE, "Suspendido", voidedChange)));
        ListItemHistory addedByVoidedNote = new ListItemHistory(item, PATIENT, details, List.of(
                new ListItemHistory.Event(item, ListItemStatus.ACTIVE, null, voidedChange)));

        assertThat(resolvedByVoidedNote.status()).isEqualTo(ListItemStatus.ACTIVE);
        assertThat(addedByVoidedNote.status()).isEqualTo(ListItemStatus.ENTERED_IN_ERROR);
        assertThat(ListItemStatus.RESOLVED.canChangeTo(ListItemStatus.ACTIVE)).isTrue();
        assertThat(ListItemStatus.ACTIVE.canChangeTo(ListItemStatus.ACTIVE)).isFalse();
    }
}
