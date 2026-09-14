package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterClosure;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.EntryType;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.json.NoteContentJsonModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

final class CanonicalPayloads {

    static final int FORMAT_VERSION = 1;

    private static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new NoteContentJsonModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .build();

    private CanonicalPayloads() {
    }

    static byte[] payload(LedgerEntry entry) {
        Map<String, Object> payload = switch (entry) {
            case LedgerEntry.EncounterOpened opened -> encounter(opened.encounter());
            case LedgerEntry.NoteSigned signed -> note(signed.patientUuid().toString(), signed.note());
            case LedgerEntry.NoteVoided voided -> voiding(voided.noteVoid());
            case LedgerEntry.EncounterClosed closed -> closure(closed.closure());
            case LedgerEntry.Unreadable unreadable ->
                    throw new IllegalArgumentException("Unreadable entry " + unreadable.entryId() + " has no canonical form");
        };
        payload.put("entryType", entry.type().name());
        return bytes(payload);
    }

    static byte[] entry(ChainLink link) {
        return entry(link.patientUuid(), link.sequence(), link.entryType(), link.entryId(), link.formatVersion(), link.payloadHash(),
                link.previousHash(), link.keyId(), link.sealedAt());
    }

    static byte[] entry(UUID patientUuid, long sequence, EntryType entryType, UUID entryId, int formatVersion, String payloadHash,
                        String previousHash, String keyId, Instant sealedAt) {
        Map<String, Object> entry = new TreeMap<>();
        entry.put("entryId", entryId.toString());
        entry.put("entryType", entryType.name());
        entry.put("formatVersion", formatVersion);
        entry.put("keyId", keyId);
        entry.put("patientUuid", patientUuid.toString());
        entry.put("payloadHash", payloadHash);
        entry.put("previousHash", previousHash);
        entry.put("sealedAt", instant(sealedAt));
        entry.put("sequence", sequence);
        return bytes(entry);
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    static String instant(Instant instant) {
        return INSTANT.format(instant);
    }

    private static Map<String, Object> encounter(Encounter encounter) {
        Map<String, Object> payload = new TreeMap<>();
        payload.put("id", encounter.id().toString());
        payload.put("patientUuid", encounter.patientUuid().toString());
        payload.put("type", encounter.type().name());
        payload.put("admissionId", encounter.admissionId());
        payload.put("openedAt", instant(encounter.openedAt()));
        payload.put("openedBy", clinician(encounter.openedBy()));
        return payload;
    }

    private static Map<String, Object> note(String patientUuid, SignedNote note) {
        Map<String, Object> author = clinician(note.author());
        author.put("email", note.signerEmail());
        Map<String, Object> payload = new TreeMap<>();
        payload.put("id", note.id().toString());
        payload.put("encounterId", note.encounterId().toString());
        payload.put("patientUuid", patientUuid);
        payload.put("type", note.type().name());
        payload.put("restriction", note.isRestricted() ? note.restriction().name() : null);
        payload.put("content", MAPPER.convertValue(note.content(), TreeMap.class));
        payload.put("author", author);
        payload.put("occurredAt", instant(note.occurredAt()));
        payload.put("recordedAt", instant(note.recordedAt()));
        payload.put("extemporaneous", note.extemporaneous());
        return payload;
    }

    private static Map<String, Object> voiding(NoteVoid noteVoid) {
        Map<String, Object> payload = new TreeMap<>();
        payload.put("noteId", noteVoid.noteId().toString());
        payload.put("reason", noteVoid.reason());
        payload.put("voidedBy", clinician(noteVoid.voidedBy()));
        payload.put("voidedAt", instant(noteVoid.voidedAt()));
        return payload;
    }

    private static Map<String, Object> closure(EncounterClosure closure) {
        Map<String, Object> payload = new TreeMap<>();
        payload.put("encounterId", closure.encounterId().toString());
        payload.put("closedAt", instant(closure.closedAt()));
        payload.put("closedBy", clinician(closure.closedBy()));
        return payload;
    }

    private static Map<String, Object> clinician(Clinician clinician) {
        Map<String, Object> value = new TreeMap<>();
        value.put("uuid", clinician.uuid().toString());
        value.put("role", clinician.role().name());
        return value;
    }

    private static byte[] bytes(Object value) {
        try {
            return MAPPER.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not build the canonical form of a clinical record entry", ex);
        }
    }
}
