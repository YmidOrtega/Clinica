package com.ClinicaDeYmid.clinical_history_service.domain.access;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public record AccessPolicy(Duration relationshipAfterClosure, Duration emergencyAccessDuration) {

    public AccessPolicy {
        Objects.requireNonNull(relationshipAfterClosure, "relationshipAfterClosure");
        Objects.requireNonNull(emergencyAccessDuration, "emergencyAccessDuration");
        if (relationshipAfterClosure.isNegative() || emergencyAccessDuration.isNegative() || emergencyAccessDuration.isZero()) {
            throw new IllegalArgumentException("access durations must be positive");
        }
    }

    public AccessDecision toPatient(Clinician clinician, Collection<CareTeamMembership> memberships,
                                    Collection<EmergencyAccess> emergencyAccesses, Instant now) {
        if (memberships.stream().anyMatch(membership -> belongsTo(membership, clinician) && membership.isActiveAt(now, relationshipAfterClosure))) {
            return new AccessDecision.Granted(AccessBasis.CARE_RELATIONSHIP);
        }
        return emergency(clinician, emergencyAccesses, now);
    }

    public AccessDecision toEncounter(Clinician clinician, UUID encounterId, Collection<CareTeamMembership> memberships,
                                      Collection<EmergencyAccess> emergencyAccesses, Instant now) {
        if (memberships.stream().anyMatch(membership -> belongsTo(membership, clinician)
                && membership.encounterId().equals(encounterId) && membership.isActiveAt(now, relationshipAfterClosure))) {
            return new AccessDecision.Granted(AccessBasis.CARE_RELATIONSHIP);
        }
        return emergency(clinician, emergencyAccesses, now);
    }

    public AccessDecision toRestrictedNote(Clinician clinician, UUID authorUuid, UUID encounterId,
                                           Collection<CareTeamMembership> memberships, Collection<EmergencyAccess> emergencyAccesses,
                                           Instant now) {
        if (clinician.uuid().equals(authorUuid)) {
            return new AccessDecision.Granted(AccessBasis.AUTHOR);
        }
        return toEncounter(clinician, encounterId, memberships, emergencyAccesses, now);
    }

    private static AccessDecision emergency(Clinician clinician, Collection<EmergencyAccess> emergencyAccesses, Instant now) {
        return emergencyAccesses.stream()
                .anyMatch(access -> access.clinician().isSamePersonAs(clinician) && access.isActiveAt(now))
                ? new AccessDecision.Granted(AccessBasis.EMERGENCY_ACCESS)
                : new AccessDecision.Denied();
    }

    private static boolean belongsTo(CareTeamMembership membership, Clinician clinician) {
        return membership.clinicianUuid().equals(clinician.uuid());
    }
}
