package com.ClinicaDeYmid.admissions_service.infrastructure.web;

final class Access {

    static final String READ = "hasAuthority('admissions:read')";
    static final String MANAGE_BEDS = "hasAuthority('admissions:manage-beds')";
    static final String MOVE_BED = "hasAuthority('admissions:move-bed')";
    static final String ADMIT = "hasAuthority('admissions:admit')";
    static final String ADMIT_OR_OVERRIDE = "hasAuthority('admissions:admit') "
            + "and (!#request.overrideCoverage() or hasAuthority('admissions:override-coverage'))";
    static final String DISCHARGE = "hasAuthority('admissions:discharge')";
    static final String CANCEL = "hasAuthority('admissions:cancel')";

    private Access() {
    }
}
