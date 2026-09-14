package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.access.EmergencyAccess;
import com.ClinicaDeYmid.clinical_history_service.domain.access.EmergencyAccesses;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.EncryptedField;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.Purpose;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
class JdbcEmergencyAccesses implements EmergencyAccesses {

    private final NamedParameterJdbcTemplate jdbc;
    private final ContentEncryption encryption;

    JdbcEmergencyAccesses(NamedParameterJdbcTemplate jdbc, ContentEncryption encryption) {
        this.jdbc = jdbc;
        this.encryption = encryption;
    }

    @Override
    public void add(EmergencyAccess access) {
        EncryptedField reason = encryption.encrypt(access.patientUuid(), Purpose.EMERGENCY_ACCESS_REASON, access.id(),
                access.reason().getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
                INSERT INTO clinical_ledger.emergency_accesses (id, patient_uuid, clinician_uuid, clinician_role, reason_key_id,
                    reason_ciphertext, granted_at, expires_at)
                VALUES (:id, :patientUuid, :clinicianUuid, :clinicianRole, :reasonKeyId, :reasonCiphertext, :grantedAt, :expiresAt)""",
                new MapSqlParameterSource()
                        .addValue("id", access.id().toString())
                        .addValue("patientUuid", access.patientUuid().toString())
                        .addValue("clinicianUuid", access.clinician().uuid().toString())
                        .addValue("clinicianRole", access.clinician().role().name())
                        .addValue("reasonKeyId", reason.dataKeyId().toString())
                        .addValue("reasonCiphertext", reason.ciphertext())
                        .addValue("grantedAt", Rows.timestamp(access.grantedAt()))
                        .addValue("expiresAt", Rows.timestamp(access.expiresAt())));
    }

    @Override
    public List<EmergencyAccess> activeFor(UUID clinicianUuid, Collection<UUID> patientUuids, Instant now) {
        if (patientUuids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT id, patient_uuid, clinician_uuid, clinician_role, reason_key_id, reason_ciphertext, granted_at, expires_at
                FROM clinical_ledger.emergency_accesses
                WHERE clinician_uuid = :clinicianUuid AND patient_uuid IN (:patients) AND expires_at > :now""",
                new MapSqlParameterSource()
                        .addValue("clinicianUuid", clinicianUuid.toString())
                        .addValue("patients", patientUuids.stream().map(UUID::toString).toList())
                        .addValue("now", Rows.timestamp(now)),
                (row, index) -> {
                    UUID id = Rows.uuid(row, "id");
                    byte[] reason = encryption.decrypt(new EncryptedField(Rows.uuid(row, "reason_key_id"), row.getBytes("reason_ciphertext")),
                            Purpose.EMERGENCY_ACCESS_REASON, id);
                    return new EmergencyAccess(id, Rows.uuid(row, "patient_uuid"), Rows.clinician(row, "clinician_uuid", "clinician_role"),
                            new String(reason, StandardCharsets.UTF_8), Rows.instant(row, "granted_at"), Rows.instant(row, "expires_at"));
                });
    }
}
