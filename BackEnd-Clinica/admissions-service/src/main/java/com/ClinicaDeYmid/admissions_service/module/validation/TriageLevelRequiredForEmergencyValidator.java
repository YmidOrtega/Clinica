package com.ClinicaDeYmid.admissions_service.module.validation;

import com.ClinicaDeYmid.admissions_service.module.dto.CreateAttentionRequestDto;
import jakarta.validation.ConstraintValidator;

public class TriageLevelRequiredForEmergencyValidator implements ConstraintValidator<TriageLevelRequiredForEmergency,
        CreateAttentionRequestDto> {

    @Override
    public boolean isValid(CreateAttentionRequestDto createAttentionRequestDto,
                           jakarta.validation.ConstraintValidatorContext context) {

        if (createAttentionRequestDto == null) {
            return true;
        }

        // Verificar si el tipo de servicio es de emergencia
        if (createAttentionRequestDto.configurationServiceId().serviceType().name().equals("EMERGENCY")) {
            // Verificar si el nivel de Triage está presente
            if (createAttentionRequestDto.triageLevel() == null) {
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
