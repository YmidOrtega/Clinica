package com.ClinicaDeYmid.module.billing.model.emun;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Specialty {
    GENERAL_MEDICINE("Medicina General"),
    CARDIOLOGY("Cardiología"),
    DERMATOLOGY("Dermatología"),
    PEDIATRICS("Pediatría"),
    GYNECOLOGY("Ginecología"),
    ORTHOPEDICS("Ortopedia"),
    NEUROLOGY("Neurología"),
    PSYCHIATRY("Psiquiatría"),
    SURGERY("Cirugía"),
    INTERNAL_MEDICINE("Medicina Interna"),
    EMERGENCY("Medicina de Urgencias"),
    ANESTHESIOLOGY("Anestesiología"),
    RADIOLOGY("Radiología"),
    PATHOLOGY("Patología"),
    OPHTHALMOLOGY("Oftalmología"),
    OTOLARYNGOLOGY("Otorrinolaringología"),
    UROLOGY("Urología"),
    ENDOCRINOLOGY("Endocrinología"),
    GASTROENTEROLOGY("Gastroenterología"),
    PULMONOLOGY("Neumología");

    private final String description;

}
