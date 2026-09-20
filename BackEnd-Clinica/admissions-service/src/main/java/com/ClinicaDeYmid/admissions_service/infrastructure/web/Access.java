package com.ClinicaDeYmid.admissions_service.infrastructure.web;

final class Access {

    static final String READ = "hasAuthority('admissions:read')";
    static final String MANAGE_BEDS = "hasAuthority('admissions:manage-beds')";
    static final String MOVE_BED = "hasAuthority('admissions:move-bed')";

    private Access() {
    }
}
