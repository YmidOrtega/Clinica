package com.ClinicaDeYmid.clinical_history_service.application.access;

import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientDirectory;
import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientLookup;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessAction;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessBasis;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessPolicy;
import com.ClinicaDeYmid.clinical_history_service.domain.access.CareTeams;
import com.ClinicaDeYmid.clinical_history_service.domain.access.EmergencyAccess;
import com.ClinicaDeYmid.clinical_history_service.domain.access.EmergencyAccesses;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class CareAccessCommands {

    private static final Logger log = LoggerFactory.getLogger(CareAccessCommands.class);

    private final PatientDirectory patients;
    private final Encounters encounters;
    private final CareTeams careTeams;
    private final EmergencyAccesses emergencyAccesses;
    private final RecordAccess access;
    private final AccessPolicy policy;
    private final TransactionOperations transactions;
    private final Clock clock;

    public CareAccessCommands(PatientDirectory patients, Encounters encounters, CareTeams careTeams, EmergencyAccesses emergencyAccesses,
                              RecordAccess access, AccessPolicy policy, TransactionOperations transactions, Clock clock) {
        this.patients = patients;
        this.encounters = encounters;
        this.careTeams = careTeams;
        this.emergencyAccesses = emergencyAccesses;
        this.access = access;
        this.policy = policy;
        this.transactions = transactions;
        this.clock = clock;
    }

    public boolean addCareTeamMember(UUID encounterId, Clinician member, Clinician addedBy) {
        Boolean added = transactions.execute(status -> {
            Encounter encounter = encounters.find(encounterId).orElseThrow(ClinicalException.EncounterNotFound::new);
            access.requireEncounter(addedBy, encounter.patientUuid(), encounterId, AccessAction.ADD_CARE_TEAM_MEMBER, member.uuid());
            encounter.requireOpen();
            return careTeams.add(encounterId, member, addedBy, Instant.now(clock));
        });
        log.info("Clinician {} added {} to the care team of encounter {}", addedBy.uuid(), member.uuid(), encounterId);
        return Boolean.TRUE.equals(added);
    }

    public EmergencyAccess grantEmergencyAccess(UUID patientUuid, String reason, Clinician clinician) {
        switch (patients.find(patientUuid)) {
            case PatientLookup.Found found -> {
            }
            case PatientLookup.NotFound notFound -> throw new ClinicalException.PatientNotFound();
            case PatientLookup.Unavailable unavailable -> throw new ClinicalException.PatientRegistryUnavailable();
        }
        EmergencyAccess grant = EmergencyAccess.grant(patientUuid, clinician, reason, policy.emergencyAccessDuration(), clock);
        transactions.executeWithoutResult(status -> {
            emergencyAccesses.add(grant);
            access.recordGranted(clinician, patientUuid, AccessAction.EMERGENCY_ACCESS, grant.id(), AccessBasis.EMERGENCY_ACCESS,
                    grant.reason());
        });
        log.warn("Emergency access {} to patient {} granted to {} until {}", grant.id(), patientUuid, clinician.uuid(), grant.expiresAt());
        return grant;
    }
}
