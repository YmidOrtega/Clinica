package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.ClinicalRole;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteRestriction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

final class Rows {

    private Rows() {
    }

    static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static UUID uuid(ResultSet row, String column) throws SQLException {
        String value = row.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    static NoteRestriction restriction(ResultSet row) throws SQLException {
        String value = row.getString("restriction");
        return value == null ? null : NoteRestriction.valueOf(value);
    }

    static Clinician clinician(ResultSet row, String uuidColumn, String roleColumn) throws SQLException {
        return new Clinician(uuid(row, uuidColumn), ClinicalRole.valueOf(row.getString(roleColumn)));
    }
}
