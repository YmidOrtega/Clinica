package com.ClinicaDeYmid.module.patient.model.emun;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Language {
    SPANISH("Español"),
    ENGLISH("Inglés"),
    FRENCH("Francés"),
    PORTUGUESE("Portugués"),
    ITALIAN("Italiano"),
    GERMAN("Alemán"),
    MANDARIN("Mandarín"),
    JAPANESE("Japonés"),
    ARABIC("Árabe"),
    RUSSIAN("Ruso");

    private final String description;
}
