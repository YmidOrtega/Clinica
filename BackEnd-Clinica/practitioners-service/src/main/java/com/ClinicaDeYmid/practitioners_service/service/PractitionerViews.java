package com.ClinicaDeYmid.practitioners_service.service;

import com.ClinicaDeYmid.practitioners_service.repository.PractitionerHistoryRepository;
import com.ClinicaDeYmid.practitioners_service.repository.entity.Practitioner;
import com.ClinicaDeYmid.practitioners_service.repository.entity.PractitionerSpecialty;
import com.ClinicaDeYmid.practitioners_service.repository.entity.PractitionerStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PractitionerViews {

    private PractitionerViews() {
    }

    public record DocumentView(String type, String label, String number) {
    }

    public record RegistrationView(String number, LocalDate registeredOn) {
    }

    public record ContactView(String email, String mobile, String phone) {
    }

    public record StatusView(String code, boolean attends, String reason, Instant since) {

        static StatusView of(PractitionerStatus status) {
            return switch (status) {
                case PractitionerStatus.Active ignored -> new StatusView(status.code().name(), true, null, null);
                case PractitionerStatus.Suspended suspended ->
                        new StatusView(status.code().name(), false, suspended.reason(), suspended.since());
                case PractitionerStatus.Retired retired ->
                        new StatusView(status.code().name(), false, retired.reason(), retired.since());
            };
        }
    }

    public record SpecialtyAssignmentView(String specialtyCode, String specialtyName, String subSpecialtyCode,
                                          String subSpecialtyName, boolean principal) {

        static SpecialtyAssignmentView of(PractitionerSpecialty assignment) {
            return new SpecialtyAssignmentView(assignment.specialty().code(), assignment.specialty().name(),
                    assignment.subSpecialty() == null ? null : assignment.subSpecialty().code(),
                    assignment.subSpecialty() == null ? null : assignment.subSpecialty().name(),
                    assignment.principal());
        }
    }

    public record PractitionerView(UUID uuid, long version, DocumentView document, String firstNames, String lastNames,
                                   String fullName, RegistrationView registration, ContactView contact,
                                   String relationship, String relationshipLabel, StatusView status,
                                   List<SpecialtyAssignmentView> specialties, Instant specialtiesAgreedAt,
                                   Instant createdAt, Instant updatedAt) {

        static PractitionerView of(Practitioner practitioner) {
            return new PractitionerView(practitioner.uuid(), practitioner.version(),
                    new DocumentView(practitioner.document().type().name(), practitioner.document().type().label(),
                            practitioner.document().number()),
                    practitioner.firstNames(), practitioner.lastNames(), practitioner.fullName(),
                    new RegistrationView(practitioner.registration().number(), practitioner.registration().registeredOn()),
                    new ContactView(practitioner.contact().email(), practitioner.contact().mobile(),
                            practitioner.contact().phone()),
                    practitioner.relationship().name(), practitioner.relationship().label(),
                    StatusView.of(practitioner.status()),
                    practitioner.specialties().stream().map(SpecialtyAssignmentView::of).toList(),
                    practitioner.specialtiesAgreedAt(), practitioner.createdAt(), practitioner.updatedAt());
        }
    }

    public record RevisionView(long number, Instant revisedAt, String revisedBy, String changeType,
                               PractitionerView state) {

        static RevisionView of(PractitionerHistoryRepository.Revision revision) {
            return new RevisionView(revision.number(), revision.revisedAt(), revision.revisedBy(),
                    revision.changeType().name(), PractitionerView.of(revision.state()));
        }
    }
}
