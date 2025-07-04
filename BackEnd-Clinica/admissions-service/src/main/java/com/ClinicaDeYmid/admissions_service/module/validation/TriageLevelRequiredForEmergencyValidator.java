package com.ClinicaDeYmid.admissions_service.module.validation;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionRequestDto;
import jakarta.validation.ConstraintValidator;

public class TriageLevelRequiredForEmergencyValidator implements ConstraintValidator<TriageLevelRequiredForEmergency,
        AttentionRequestDto> {

    @Override
    public boolean isValid(AttentionRequestDto attentionRequestDto,
                           jakarta.validation.ConstraintValidatorContext context) {

        if (attentionRequestDto == null) {
            return true;
        }

        if (attentionRequestDto.configurationServiceId() == 4L) {
            // Verificar si el nivel de Triage está presente
            if (attentionRequestDto.triageLevel() == null) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        "El nivel de Triage es obligatorio para atenciones de emergencia")
                        .addPropertyNode("triageLevel")
                        .addConstraintViolation();
                return false;
            }
        }


        return true;
    }

}
