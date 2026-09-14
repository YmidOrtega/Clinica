package com.ClinicaDeYmid.clinical_history_service.domain.note;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.ClinicalRole;

import java.util.EnumSet;
import java.util.Set;

public enum NoteType {
    ADMISSION(EnumSet.of(ClinicalRole.DOCTOR)),
    PROGRESS(EnumSet.of(ClinicalRole.DOCTOR)),
    TRIAGE(EnumSet.of(ClinicalRole.DOCTOR, ClinicalRole.NURSE)),
    CONSULTATION(EnumSet.of(ClinicalRole.DOCTOR)),
    NURSING(EnumSet.of(ClinicalRole.NURSE)),
    DISCHARGE(EnumSet.of(ClinicalRole.DOCTOR)),
    ADDENDUM(EnumSet.of(ClinicalRole.DOCTOR, ClinicalRole.NURSE));

    private final Set<ClinicalRole> authors;

    NoteType(Set<ClinicalRole> authors) {
        this.authors = authors;
    }

    public boolean canBeWrittenBy(ClinicalRole role) {
        return authors.contains(role);
    }
}
