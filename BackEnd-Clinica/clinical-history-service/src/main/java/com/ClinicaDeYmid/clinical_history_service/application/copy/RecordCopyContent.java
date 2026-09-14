package com.ClinicaDeYmid.clinical_history_service.application.copy;

import com.ClinicaDeYmid.clinical_history_service.application.record.ClinicalRecordQueries.EncounterRecord;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainVerification;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemHistory;
import com.ClinicaDeYmid.clinical_history_service.domain.update.VitalSignObservation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RecordCopyContent(
        UUID copyId,
        PatientReference patient,
        List<PatientReference> linkedRecords,
        Instant generatedAt,
        UUID requestedBy,
        String requestedRole,
        String reason,
        Instant periodFrom,
        Instant periodTo,
        List<EncounterRecord> encounters,
        List<ListItemHistory> listItems,
        List<VitalSignObservation> vitalSigns,
        List<ChainVerification> chains,
        Map<LedgerEntry.Key, ChainLink> links,
        String sealAlgorithm,
        String activeSealKeyId) {
}
