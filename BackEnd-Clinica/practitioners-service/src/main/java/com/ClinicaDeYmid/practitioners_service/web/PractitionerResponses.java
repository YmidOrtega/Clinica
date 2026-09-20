package com.ClinicaDeYmid.practitioners_service.web;

import com.ClinicaDeYmid.practitioners_service.service.PractitionerViews;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class PractitionerResponses {

    private PractitionerResponses() {
    }

    record DocumentView(String type, String label, String number) {
    }

    record RegistrationView(String number, LocalDate registeredOn) {
    }

    record ContactView(String email, String mobile, String phone) {
    }

    record StatusView(String code, boolean attends, String reason, Instant since) {
    }

    record AccountView(boolean linked, UUID userUuid, String state) {
    }

    record SpecialtyView(String specialtyCode, String specialtyName, String subSpecialtyCode, String subSpecialtyName,
                         boolean principal) {
    }

    record PractitionerView(UUID uuid, long version, DocumentView document, String firstNames, String lastNames,
                            String fullName, RegistrationView registration, ContactView contact, String relationship,
                            String relationshipLabel, StatusView status, List<SpecialtyView> specialties,
                            Instant specialtiesAgreedAt, AccountView account, Instant createdAt, Instant updatedAt) {

        static PractitionerView from(PractitionerViews.PractitionerView practitioner) {
            return new PractitionerView(practitioner.uuid(), practitioner.version(),
                    new DocumentView(practitioner.document().type(), practitioner.document().label(),
                            practitioner.document().number()),
                    practitioner.firstNames(), practitioner.lastNames(), practitioner.fullName(),
                    new RegistrationView(practitioner.registration().number(), practitioner.registration().registeredOn()),
                    new ContactView(practitioner.contact().email(), practitioner.contact().mobile(),
                            practitioner.contact().phone()),
                    practitioner.relationship(), practitioner.relationshipLabel(),
                    new StatusView(practitioner.status().code(), practitioner.status().attends(),
                            practitioner.status().reason(), practitioner.status().since()),
                    practitioner.specialties().stream()
                            .map(specialty -> new SpecialtyView(specialty.specialtyCode(), specialty.specialtyName(),
                                    specialty.subSpecialtyCode(), specialty.subSpecialtyName(), specialty.principal()))
                            .toList(),
                    practitioner.specialtiesAgreedAt(),
                    new AccountView(practitioner.account().linked(), practitioner.account().userUuid(),
                            practitioner.account().state()),
                    practitioner.createdAt(), practitioner.updatedAt());
        }
    }

    record RevisionView(long number, Instant revisedAt, String revisedBy, String changeType,
                        PractitionerView state) {

        static RevisionView from(PractitionerViews.RevisionView revision) {
            return new RevisionView(revision.number(), revision.revisedAt(), revision.revisedBy(),
                    revision.changeType(), PractitionerView.from(revision.state()));
        }
    }
}
