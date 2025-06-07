package com.ClinicaDeYmid.patient_service.module.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ParentsRequiredForMinorValidator.class)
@Documented
public @interface ParentsRequiredForMinor {

    String message() default "Los nombres de los padres son obligatorios para pacientes menores de 18 años";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}