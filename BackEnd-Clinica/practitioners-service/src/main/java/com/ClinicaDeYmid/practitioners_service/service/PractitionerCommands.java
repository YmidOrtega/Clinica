package com.ClinicaDeYmid.practitioners_service.service;

import com.ClinicaDeYmid.practitioners_service.shared.DocumentType;
import com.ClinicaDeYmid.practitioners_service.shared.RelationshipType;
import com.ClinicaDeYmid.practitioners_service.shared.PractitionerStatusCode;

import java.time.LocalDate;
import java.util.List;

public final class PractitionerCommands {

    private PractitionerCommands() {
    }

    public record Document(DocumentType type, String number) {
    }

    public record Registration(String number, LocalDate registeredOn) {
    }

    public record Contact(String email, String mobile, String phone) {
    }

    public record NewPractitioner(Document document, String firstNames, String lastNames, Registration registration,
                                  Contact contact, RelationshipType relationship) {
    }

    public record Identity(Document document, String firstNames, String lastNames) {
    }

    public record SpecialtyAssignment(String specialtyCode, String subSpecialtyCode, boolean principal) {
    }

    public record Assignments(List<SpecialtyAssignment> specialties) {

        public Assignments {
            specialties = specialties == null ? List.of() : List.copyOf(specialties);
        }
    }

    public record Search(Document document, String registrationNumber, String lastNames, String specialtyCode,
                         java.util.UUID authUserUuid, PractitionerStatusCode status) {
    }
}
