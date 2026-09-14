package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.domain.update.AppliedUpdate;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListCategory;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemDetails;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemHistory;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemStatus;
import com.ClinicaDeYmid.clinical_history_service.domain.update.NoteOrigin;
import com.ClinicaDeYmid.clinical_history_service.domain.update.PatientChart;
import com.ClinicaDeYmid.clinical_history_service.domain.update.VitalSignKind;
import com.ClinicaDeYmid.clinical_history_service.domain.update.VitalSignObservation;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.EncryptedField;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.Purpose;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
class JdbcPatientChart implements PatientChart {

    private static final String ORIGIN_COLUMNS = """
            n.id AS origin_note_id, n.encounter_id AS origin_encounter_id, n.author_uuid AS origin_author_uuid,
            n.author_role AS origin_author_role, n.restriction, n.recorded_at AS origin_recorded_at,
            EXISTS (SELECT 1 FROM clinical_ledger.note_voids v WHERE v.note_id = n.id) AS origin_voided""";

    private final NamedParameterJdbcTemplate jdbc;
    private final ContentEncryption encryption;

    JdbcPatientChart(NamedParameterJdbcTemplate jdbc, ContentEncryption encryption) {
        this.jdbc = jdbc;
        this.encryption = encryption;
    }

    void append(UUID patientUuid, SignedNote note) {
        for (AppliedUpdate update : note.updates()) {
            switch (update) {
                case AppliedUpdate.ListItemAdded added -> insertEvent(added.id(), added.id(), patientUuid, added.details().category(),
                        "ADDED", ListItemStatus.ACTIVE, NoteContentColumn.writeDetails(added.details()), note.id());
                case AppliedUpdate.ListItemStatusChanged changed -> insertEvent(changed.id(), changed.itemId(), patientUuid,
                        changed.category(), "STATUS_CHANGED", changed.status(), changed.reason().getBytes(StandardCharsets.UTF_8), note.id());
                case AppliedUpdate.VitalSignObserved observed -> jdbc.update("""
                        INSERT INTO clinical_ledger.vital_sign_observations (id, patient_uuid, note_id, kind, value, measured_at)
                        VALUES (:id, :patientUuid, :noteId, :kind, :value, :measuredAt)""",
                        new MapSqlParameterSource()
                                .addValue("id", observed.id().toString())
                                .addValue("patientUuid", patientUuid.toString())
                                .addValue("noteId", note.id().toString())
                                .addValue("kind", observed.kind().name())
                                .addValue("value", observed.value())
                                .addValue("measuredAt", Rows.timestamp(observed.measuredAt())));
            }
        }
    }

    Map<UUID, List<AppliedUpdate>> updatesOf(Collection<UUID> noteIds) {
        Map<UUID, List<AppliedUpdate>> updates = new LinkedHashMap<>();
        if (noteIds.isEmpty()) {
            return updates;
        }
        MapSqlParameterSource notes = new MapSqlParameterSource("notes", noteIds.stream().map(UUID::toString).toList());
        jdbc.query("""
                SELECT id, item_id, category, event_type, status, payload_key_id, payload_ciphertext, note_id
                FROM clinical_ledger.list_item_events WHERE note_id IN (:notes)""", notes, (ResultSet row) -> {
            UUID id = Rows.uuid(row, "id");
            byte[] payload = decrypt(row, id);
            AppliedUpdate update = "ADDED".equals(row.getString("event_type"))
                    ? new AppliedUpdate.ListItemAdded(id, NoteContentColumn.readDetails(payload, id))
                    : new AppliedUpdate.ListItemStatusChanged(id, Rows.uuid(row, "item_id"), ListCategory.valueOf(row.getString("category")),
                    ListItemStatus.valueOf(row.getString("status")), new String(payload, StandardCharsets.UTF_8));
            updates.computeIfAbsent(Rows.uuid(row, "note_id"), note -> new ArrayList<>()).add(update);
        });
        jdbc.query("""
                SELECT id, note_id, kind, value, measured_at FROM clinical_ledger.vital_sign_observations WHERE note_id IN (:notes)""",
                notes, (ResultSet row) -> {
                    updates.computeIfAbsent(Rows.uuid(row, "note_id"), note -> new ArrayList<>()).add(new AppliedUpdate.VitalSignObserved(
                            Rows.uuid(row, "id"), VitalSignKind.valueOf(row.getString("kind")), row.getBigDecimal("value").stripTrailingZeros(),
                            Rows.instant(row, "measured_at")));
                });
        return updates;
    }

    @Override
    public List<ListItemHistory> listItemsOf(Collection<UUID> patientUuids) {
        if (patientUuids.isEmpty()) {
            return List.of();
        }
        record Row(UUID id, UUID itemId, UUID patientUuid, String eventType, ListItemStatus status, byte[] payload, NoteOrigin origin) {
        }
        List<Row> rows = jdbc.query("""
                SELECT e.id, e.item_id, e.patient_uuid, e.event_type, e.status, e.payload_key_id, e.payload_ciphertext, %s
                FROM clinical_ledger.list_item_events e
                JOIN clinical_ledger.notes n ON n.id = e.note_id
                WHERE e.patient_uuid IN (:patients)""".formatted(ORIGIN_COLUMNS),
                new MapSqlParameterSource("patients", patientUuids.stream().map(UUID::toString).toList()),
                (row, index) -> new Row(Rows.uuid(row, "id"), Rows.uuid(row, "item_id"), Rows.uuid(row, "patient_uuid"),
                        row.getString("event_type"), ListItemStatus.valueOf(row.getString("status")), decrypt(row, Rows.uuid(row, "id")),
                        origin(row)));
        return rows.stream().collect(Collectors.groupingBy(Row::itemId, LinkedHashMap::new, Collectors.toList())).entrySet().stream()
                .filter(item -> item.getValue().stream().anyMatch(event -> "ADDED".equals(event.eventType())))
                .map(item -> {
                    Row added = item.getValue().stream().filter(event -> "ADDED".equals(event.eventType())).findFirst().orElseThrow();
                    ListItemDetails details = NoteContentColumn.readDetails(added.payload(), added.itemId());
                    List<ListItemHistory.Event> events = item.getValue().stream()
                            .map(event -> new ListItemHistory.Event(event.id(), event.status(),
                                    "ADDED".equals(event.eventType()) ? null : new String(event.payload(), StandardCharsets.UTF_8), event.origin()))
                            .toList();
                    return new ListItemHistory(item.getKey(), added.patientUuid(), details, events);
                })
                .toList();
    }

    @Override
    public List<VitalSignObservation> vitalSignsOf(Collection<UUID> patientUuids, VitalSignKind kind, Instant from, Instant to) {
        if (patientUuids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT o.id, o.patient_uuid, o.kind, o.value, o.measured_at, %s
                FROM clinical_ledger.vital_sign_observations o
                JOIN clinical_ledger.notes n ON n.id = o.note_id
                WHERE o.patient_uuid IN (:patients)
                  AND (:kind IS NULL OR o.kind = :kind)
                  AND (:from IS NULL OR o.measured_at >= :from)
                  AND (:to IS NULL OR o.measured_at < :to)
                ORDER BY o.measured_at, o.kind
                LIMIT 5000""".formatted(ORIGIN_COLUMNS),
                new MapSqlParameterSource()
                        .addValue("patients", patientUuids.stream().map(UUID::toString).toList())
                        .addValue("kind", kind == null ? null : kind.name())
                        .addValue("from", from == null ? null : Rows.timestamp(from))
                        .addValue("to", to == null ? null : Rows.timestamp(to)),
                (row, index) -> new VitalSignObservation(Rows.uuid(row, "id"), Rows.uuid(row, "patient_uuid"),
                        VitalSignKind.valueOf(row.getString("kind")), row.getBigDecimal("value").stripTrailingZeros(),
                        Rows.instant(row, "measured_at"), origin(row)));
    }

    private void insertEvent(UUID id, UUID itemId, UUID patientUuid, ListCategory category, String type, ListItemStatus status, byte[] payload,
                             UUID noteId) {
        EncryptedField encrypted = encryption.encrypt(patientUuid, Purpose.LIST_ITEM_PAYLOAD, id, payload);
        jdbc.update("""
                INSERT INTO clinical_ledger.list_item_events (id, item_id, patient_uuid, category, event_type, status, payload_key_id,
                    payload_ciphertext, note_id)
                VALUES (:id, :itemId, :patientUuid, :category, :type, :status, :keyId, :ciphertext, :noteId)""",
                new MapSqlParameterSource()
                        .addValue("id", id.toString())
                        .addValue("itemId", itemId.toString())
                        .addValue("patientUuid", patientUuid.toString())
                        .addValue("category", category.name())
                        .addValue("type", type)
                        .addValue("status", status.name())
                        .addValue("keyId", encrypted.dataKeyId().toString())
                        .addValue("ciphertext", encrypted.ciphertext())
                        .addValue("noteId", noteId.toString()));
    }

    private byte[] decrypt(ResultSet row, UUID id) throws SQLException {
        return encryption.decrypt(new EncryptedField(Rows.uuid(row, "payload_key_id"), row.getBytes("payload_ciphertext")),
                Purpose.LIST_ITEM_PAYLOAD, id);
    }

    private static NoteOrigin origin(ResultSet row) throws SQLException {
        return new NoteOrigin(Rows.uuid(row, "origin_note_id"), Rows.uuid(row, "origin_encounter_id"),
                Rows.clinician(row, "origin_author_uuid", "origin_author_role"), Rows.restriction(row), Rows.instant(row, "origin_recorded_at"),
                row.getBoolean("origin_voided"));
    }
}
