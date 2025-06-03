package com.ClinicaDeYmid.patient_service.module.patient.validation;

import com.ClinicaDeYmid.patient_service.module.patient.dto.PatientDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import java.time.Period;

public class ParentsRequiredForMinorValidator implements ConstraintValidator<ParentsRequiredForMinor, PatientDTO> {

    @Override
    public void initialize(ParentsRequiredForMinor constraintAnnotation) {
        // Inicialización si es necesaria
    }

    @Override
    public boolean isValid(PatientDTO patientDTO, ConstraintValidatorContext context) {
        if (patientDTO == null || patientDTO.dateOfBirth() == null) {
            return true; // Otras validaciones se encargarán de null checks
        }

        // Calcular la edad del paciente
        int age = Period.between(patientDTO.dateOfBirth(), LocalDate.now()).getYears();

        // Si es menor de 18 años, verificar que tenga nombres de padres
        if (age < 18) {
            boolean mothersNameValid = patientDTO.mothersName() != null && !patientDTO.mothersName().trim().isEmpty();
            boolean fathersNameValid = patientDTO.fathersName() != null && !patientDTO.fathersName().trim().isEmpty();

            if (!mothersNameValid || !fathersNameValid) {
                context.disableDefaultConstraintViolation();

                if (!mothersNameValid) {
                    context.buildConstraintViolationWithTemplate(
                                    "El nombre de la madre es obligatorio para pacientes menores de 18 años")
                            .addPropertyNode("mothersName")
                            .addConstraintViolation();
                }

                if (!fathersNameValid) {
                    context.buildConstraintViolationWithTemplate(
                                    "El nombre del padre es obligatorio para pacientes menores de 18 años")
                            .addPropertyNode("fathersName")
                            .addConstraintViolation();
                }

                return false;
            }
        }

        return true;
    }
}