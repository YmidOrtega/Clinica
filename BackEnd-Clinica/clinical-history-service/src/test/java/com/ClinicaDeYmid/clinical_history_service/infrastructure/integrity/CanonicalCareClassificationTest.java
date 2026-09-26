package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.CareSetting;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterStatus;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.note.CareReason;
import com.ClinicaDeYmid.clinical_history_service.domain.note.Diagnosis;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.AT;
import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.ENCOUNTER;
import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.NOTE;
import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.NURSE;
import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.PATIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalCareClassificationTest {

    @Test
    void entriesWithoutClassificationKeepTheCanonicalFormTheyWereSealedWith() {
        Encounter legacy = new Encounter(ENCOUNTER, PATIENT, EncounterType.OUTPATIENT, null, false, AT, NURSE,
                new EncounterStatus.Open());

        String canonical = text(new LedgerEntry.EncounterOpened(legacy));

        assertThat(canonical).isEqualTo("{\"admissionVerified\":false,\"entryType\":\"ENCOUNTER_OPENED\","
                + "\"id\":\"8a1d2c3b-4e5f-4a6b-8c7d-9e0f1a2b3c4d\",\"openedAt\":\"2026-09-10T14:00:00.123456Z\","
                + "\"openedBy\":{\"role\":\"NURSE\",\"uuid\":\"00000000-0000-4000-8000-000000000004\"},"
                + "\"patientUuid\":\"3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d\",\"type\":\"OUTPATIENT\"}");
        assertThat(text(consultation(null))).doesNotContain("careReason");
    }

    @Test
    void theClassificationIsSealedWithTheEntry() {
        Encounter classified = new Encounter(ENCOUNTER, PATIENT, EncounterType.OUTPATIENT, null, false,
                new CareSetting("328", "01", "01"), AT, NURSE, new EncounterStatus.Open());

        assertThat(text(new LedgerEntry.EncounterOpened(classified)))
                .contains("\"careSetting\":{\"modality\":\"01\",\"serviceCode\":\"328\",\"serviceGroup\":\"01\"}");
        assertThat(text(consultation(new CareReason("15", "38"))))
                .contains("\"careReason\":{\"cause\":\"38\",\"purpose\":\"15\"}");
    }

    @Test
    void refusesMalformedCodes() {
        assertThatThrownBy(() -> new CareSetting("32", "01", "01")).isInstanceOf(ClinicalException.InvalidData.class);
        assertThatThrownBy(() -> new CareSetting("328", "10", "01")).isInstanceOf(ClinicalException.InvalidData.class);
        assertThatThrownBy(() -> new CareReason("1", null)).isInstanceOf(ClinicalException.InvalidData.class);
        assertThatThrownBy(() -> new CareReason(" ", null)).isInstanceOf(ClinicalException.InvalidData.class);
        assertThat(new CareReason(null, "38").purpose()).isNull();
    }

    private static LedgerEntry consultation(CareReason reason) {
        return new LedgerEntry.NoteSigned(PATIENT, new SignedNote(NOTE, ENCOUNTER, NURSE, "doctor@clinica.test",
                new NoteContent.Consultation("Medicina general", "Control", "Sano", "Nada",
                        List.of(new Diagnosis("Z000", Diagnosis.Role.PRINCIPAL, Diagnosis.Type.CONFIRMED_NEW, "Examen", "2026")),
                        reason), null, List.of(), List.of(), AT, AT.plusSeconds(300), false));
    }

    private static String text(LedgerEntry entry) {
        return new String(CanonicalPayloads.payload(entry), StandardCharsets.UTF_8);
    }
}
