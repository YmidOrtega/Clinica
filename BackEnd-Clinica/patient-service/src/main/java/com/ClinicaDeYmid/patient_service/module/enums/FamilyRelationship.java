package com.ClinicaDeYmid.patient_service.module.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FamilyRelationship {
    FATHER("Padre"),
    MOTHER("Madre"),
    BROTHER("Hermano"),
    SISTER("Hermana"),
    PATERNAL_GRANDFATHER("Abuelo paterno"),
    PATERNAL_GRANDMOTHER("Abuela paterna"),
    MATERNAL_GRANDFATHER("Abuelo materno"),
    MATERNAL_GRANDMOTHER("Abuela materna"),
    SON("Hijo"),
    DAUGHTER("Hija"),
    UNCLE("Tío"),
    AUNT("Tía"),
    COUSIN("Primo/Prima"),
    OTHER("Otro");

    private final String displayName;
}