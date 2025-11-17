package com.ClinicaDeYmid.patient_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AllergyReactionType {
    RESPIRATORY("Respiratoria", "Dificultad para respirar, sibilancias, tos"),
    SKIN("Cutánea", "Erupciones, urticaria, picazón, enrojecimiento"),
    GASTROINTESTINAL("Gastrointestinal", "Náuseas, vómitos, diarrea, dolor abdominal"),
    CARDIOVASCULAR("Cardiovascular", "Taquicardia, hipotensión, shock"),
    NEUROLOGICAL("Neurológica", "Mareos, confusión, pérdida de conciencia"),
    OCULAR("Ocular", "Enrojecimiento, picazón, lagrimeo"),
    ANAPHYLAXIS("Anafilaxia", "Reacción sistémica grave que afecta múltiples órganos"),
    OTHER("Otra", "Otro tipo de reacción alérgica");

    private final String displayName;
    private final String description;
}