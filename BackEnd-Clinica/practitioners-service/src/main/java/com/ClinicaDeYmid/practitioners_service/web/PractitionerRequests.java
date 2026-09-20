package com.ClinicaDeYmid.practitioners_service.web;

import com.ClinicaDeYmid.practitioners_service.shared.DocumentType;
import com.ClinicaDeYmid.practitioners_service.shared.RelationshipType;
import com.ClinicaDeYmid.practitioners_service.service.PractitionerCommands;
import com.ClinicaDeYmid.practitioners_service.shared.PractitionerStatusCode;

import java.time.LocalDate;
import java.util.List;

final class PractitionerRequests {

    private PractitionerRequests() {
    }

    record Document(DocumentType type, String number) {

        PractitionerCommands.Document toCommand() {
            return new PractitionerCommands.Document(type, number);
        }
    }

    record Registration(String number, LocalDate registeredOn) {

        PractitionerCommands.Registration toCommand() {
            return new PractitionerCommands.Registration(number, registeredOn);
        }
    }

    record Contact(String email, String mobile, String phone) {

        PractitionerCommands.Contact toCommand() {
            return new PractitionerCommands.Contact(email, mobile, phone);
        }
    }

    record NewPractitioner(Document document, String firstNames, String lastNames, Registration registration,
                           Contact contact, RelationshipType relationship) {

        PractitionerCommands.NewPractitioner toCommand() {
            return new PractitionerCommands.NewPractitioner(document == null ? null : document.toCommand(),
                    firstNames, lastNames, registration == null ? null : registration.toCommand(),
                    contact == null ? null : contact.toCommand(), relationship);
        }
    }

    record Identity(Document document, String firstNames, String lastNames) {

        PractitionerCommands.Identity toCommand() {
            return new PractitionerCommands.Identity(document == null ? null : document.toCommand(),
                    firstNames, lastNames);
        }
    }

    record Relationship(RelationshipType relationship) {
    }

    record SpecialtyAssignment(String specialtyCode, String subSpecialtyCode, boolean principal) {

        PractitionerCommands.SpecialtyAssignment toCommand() {
            return new PractitionerCommands.SpecialtyAssignment(specialtyCode, subSpecialtyCode, principal);
        }
    }

    record Assignments(List<SpecialtyAssignment> specialties) {

        PractitionerCommands.Assignments toCommand() {
            return new PractitionerCommands.Assignments(specialties == null ? List.of()
                    : specialties.stream().map(SpecialtyAssignment::toCommand).toList());
        }
    }

    record StatusChange(String reason) {
    }

    record AccountLink(java.util.UUID userUuid) {
    }

    record Search(Document document, String registrationNumber, String lastNames, String specialtyCode,
                  java.util.UUID authUserUuid, PractitionerStatusCode status) {

        PractitionerCommands.Search toCommand() {
            return new PractitionerCommands.Search(document == null ? null : document.toCommand(),
                    registrationNumber, lastNames, specialtyCode, authUserUuid, status);
        }
    }
}
