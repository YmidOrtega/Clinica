package com.ClinicaDeYmid.patient_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MedicationRoute {
    ORAL("Oral", "Administración por vía oral"),
    INTRAVENOUS("Intravenosa", "Administración por vía intravenosa"),
    INTRAMUSCULAR("Intramuscular", "Administración por vía intramuscular"),
    SUBCUTANEOUS("Subcutánea", "Administración por vía subcutánea"),
    TOPICAL("Tópica", "Aplicación sobre la piel"),
    INHALATION("Inhalatoria", "Administración por inhalación"),
    RECTAL("Rectal", "Administración por vía rectal"),
    OPHTHALMIC("Oftálmica", "Aplicación en los ojos"),
    OTIC("Ótica", "Aplicación en los oídos"),
    NASAL("Nasal", "Administración por vía nasal"),
    TRANSDERMAL("Transdérmica", "Parche transdérmico"),
    SUBLINGUAL("Sublingual", "Administración debajo de la lengua"),
    OTHER("Otra", "Otra vía de administración");

    private final String displayName;
    private final String description;
}