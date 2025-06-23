package com.ClinicaDeYmid.admissions_service.module.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TriageLevelRequiredForEmergencyValidator.class)
@Documented
public @interface TriageLevelRequiredForEmergency {

    String message() default "El nivel de Triage es obligatorio para atenciones de emergencia";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
