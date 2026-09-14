package com.ClinicaDeYmid.clinical_history_service.application.access;

import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientDirectory;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessAction;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessBasis;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessDecision;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessPolicy;
import com.ClinicaDeYmid.clinical_history_service.domain.access.CareTeamMembership;
import com.ClinicaDeYmid.clinical_history_service.domain.access.CareTeams;
import com.ClinicaDeYmid.clinical_history_service.domain.access.EmergencyAccess;
import com.ClinicaDeYmid.clinical_history_service.domain.access.EmergencyAccesses;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class RecordAccess {

    private final PatientDirectory patients;
    private final CareTeams careTeams;
    private final EmergencyAccesses emergencyAccesses;
    private final AccessPolicy policy;
    private final AccessAudit audit;
    private final Clock clock;

    public RecordAccess(PatientDirectory patients, CareTeams careTeams, EmergencyAccesses emergencyAccesses, AccessPolicy policy,
                        AccessAudit audit, Clock clock) {
        this.patients = patients;
        this.careTeams = careTeams;
        this.emergencyAccesses = emergencyAccesses;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccessBasis requirePatient(Clinician clinician, UUID patientUuid, AccessAction action, UUID resourceId) {
        Context context = contextOf(clinician, patientUuid);
        return granted(context, clinician, patientUuid, action, resourceId, false,
                policy.toPatient(clinician, context.memberships(), context.emergencyAccesses(), context.now()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccessBasis requireEncounter(Clinician clinician, UUID patientUuid, UUID encounterId, AccessAction action, UUID resourceId) {
        Context context = contextOf(clinician, patientUuid);
        return granted(context, clinician, patientUuid, action, resourceId, false,
                policy.toEncounter(clinician, encounterId, context.memberships(), context.emergencyAccesses(), context.now()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccessBasis requireNote(Clinician clinician, UUID patientUuid, SignedNote note) {
        Context context = contextOf(clinician, patientUuid);
        AccessDecision toPatient = policy.toPatient(clinician, context.memberships(), context.emergencyAccesses(), context.now());
        if (!note.isRestricted() || toPatient instanceof AccessDecision.Denied) {
            return granted(context, clinician, patientUuid, AccessAction.READ_NOTE, note.id(), note.isRestricted(), toPatient);
        }
        AccessDecision toRestricted = policy.toRestrictedNote(clinician, note.author().uuid(), note.encounterId(), context.memberships(),
                context.emergencyAccesses(), context.now());
        if (toRestricted instanceof AccessDecision.Granted granted) {
            audit.record(new AccessEvent(patientUuid, clinician, AccessAction.READ_NOTE, note.id(), AccessEvent.Outcome.GRANTED,
                    granted.basis(), true, emergencyReason(context, granted.basis()), context.now()));
            return granted.basis();
        }
        throw ClinicalException.AccessDenied.restrictedNote(clinician, patientUuid, note.id());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordGranted(Clinician clinician, UUID patientUuid, AccessAction action, UUID resourceId, AccessBasis basis,
                              String emergencyReason) {
        audit.record(new AccessEvent(patientUuid, clinician, action, resourceId, AccessEvent.Outcome.GRANTED, basis, false,
                emergencyReason, Instant.now(clock)));
    }

    @Transactional
    public void recordDenied(ClinicalException.AccessDenied denied) {
        audit.record(new AccessEvent(denied.patientUuid(), denied.actor(), denied.action(), denied.resourceId(),
                AccessEvent.Outcome.DENIED, null, false, null, Instant.now(clock)));
    }

    private AccessBasis granted(Context context, Clinician clinician, UUID patientUuid, AccessAction action, UUID resourceId,
                                boolean restricted, AccessDecision decision) {
        return switch (decision) {
            case AccessDecision.Granted granted -> {
                audit.record(new AccessEvent(patientUuid, clinician, action, resourceId, AccessEvent.Outcome.GRANTED, granted.basis(),
                        restricted, emergencyReason(context, granted.basis()), context.now()));
                yield granted.basis();
            }
            case AccessDecision.Denied denied ->
                    throw ClinicalException.AccessDenied.careRelationshipRequired(clinician, patientUuid, action, resourceId);
        };
    }

    private Context contextOf(Clinician clinician, UUID patientUuid) {
        List<UUID> subjects = patients.samePersonSubjects(patientUuid);
        Instant now = Instant.now(clock);
        return new Context(careTeams.membershipsOf(clinician.uuid(), subjects), emergencyAccesses.activeFor(clinician.uuid(), subjects, now),
                now);
    }

    private static String emergencyReason(Context context, AccessBasis basis) {
        return basis != AccessBasis.EMERGENCY_ACCESS ? null : context.emergencyAccesses().stream()
                .max(Comparator.comparing(EmergencyAccess::grantedAt))
                .map(EmergencyAccess::reason)
                .orElse(null);
    }

    private record Context(List<CareTeamMembership> memberships, List<EmergencyAccess> emergencyAccesses, Instant now) {
    }
}
