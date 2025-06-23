package com.ClinicaDeYmid.patient_service.module.validation;

import com.ClinicaDeYmid.patient_service.module.dto.NewPatientDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import java.time.Period;

public class ParentsRequiredForMinorValidator implements ConstraintValidator<ParentsRequiredForMinor, NewPatientDto> {


    @Override
    public boolean isValid(NewPatientDto newPatientDTO, ConstraintValidatorContext context) {
        if (newPatientDTO == null || newPatientDTO.dateOfBirth() == null) {
            return true; // Otras validaciones se encargarán de null checks
        }

        // Calcular la edad del paciente
        int age = Period.between(newPatientDTO.dateOfBirth(), LocalDate.now()).getYears();

        // Si es menor de 18 años, verificar que tenga nombres de padres
        if (age < 18) {
            boolean mothersNameValid = newPatientDTO.mothersName() != null && !newPatientDTO.mothersName().trim().isEmpty();
            boolean fathersNameValid = newPatientDTO.fathersName() != null && !newPatientDTO.fathersName().trim().isEmpty();

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