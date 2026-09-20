package com.ClinicaDeYmid.practitioners_service.repository;

import com.ClinicaDeYmid.practitioners_service.repository.entity.Practitioner;
import com.ClinicaDeYmid.practitioners_service.repository.entity.PractitionerStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record PractitionerMessage(UUID eventId, String type, Instant occurredAt, String traceId, UUID practitionerUuid,
                           long version, DocumentView document, String firstNames, String lastNames, String fullName,
                           RegistrationView registration, ContactView contact, String relationship,
                           StatusView status, List<SpecialtyView> specialties, UUID authUserUuid) {

    static final String AGGREGATE_TYPE = "practitioners";

    static PractitionerMessage of(Practitioner practitioner, String type, UUID eventId, Instant occurredAt,
                                  String traceId) {
        return new PractitionerMessage(eventId, type, occurredAt, traceId, practitioner.uuid(), practitioner.version(),
                new DocumentView(practitioner.document().type().name(), practitioner.document().number()),
                practitioner.firstNames(), practitioner.lastNames(), practitioner.fullName(),
                new RegistrationView(practitioner.registration().number(), practitioner.registration().registeredOn()),
                new ContactView(practitioner.contact().email(), practitioner.contact().mobile()),
                practitioner.relationship().name(), StatusView.of(practitioner.status()),
                practitioner.specialties().stream()
                        .map(assignment -> new SpecialtyView(assignment.specialty().code(),
                                assignment.specialty().name(),
                                assignment.subSpecialty() == null ? null : assignment.subSpecialty().code(),
                                assignment.subSpecialty() == null ? null : assignment.subSpecialty().name(),
                                assignment.principal()))
                        .toList(),
                practitioner.authUserUuid());
    }

    record DocumentView(String type, String number) {
    }

    record RegistrationView(String number, LocalDate registeredOn) {
    }

    record ContactView(String email, String mobile) {
    }

    record StatusView(String code, String reason, Instant since) {

        static StatusView of(PractitionerStatus status) {
            return switch (status) {
                case PractitionerStatus.Active ignored -> new StatusView(status.code().name(), null, null);
                case PractitionerStatus.Suspended suspended ->
                        new StatusView(status.code().name(), suspended.reason(), suspended.since());
                case PractitionerStatus.Retired retired ->
                        new StatusView(status.code().name(), retired.reason(), retired.since());
            };
        }
    }

    record SpecialtyView(String specialtyCode, String specialtyName, String subSpecialtyCode, String subSpecialtyName,
                         boolean principal) {
    }
}
