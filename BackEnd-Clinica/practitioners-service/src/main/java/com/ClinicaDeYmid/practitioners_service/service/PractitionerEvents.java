package com.ClinicaDeYmid.practitioners_service.service;

public final class PractitionerEvents {

    public static final String REGISTERED = "PractitionerRegistered";
    public static final String IDENTITY_CORRECTED = "PractitionerIdentityCorrected";
    public static final String REGISTRATION_CORRECTED = "PractitionerRegistrationCorrected";
    public static final String CONTACT_UPDATED = "PractitionerContactUpdated";
    public static final String RELATIONSHIP_AGREED = "PractitionerRelationshipAgreed";
    public static final String SPECIALTIES_ASSIGNED = "PractitionerSpecialtiesAssigned";
    public static final String SUSPENDED = "PractitionerSuspended";
    public static final String RETIRED = "PractitionerRetired";
    public static final String REINSTATED = "PractitionerReinstated";
    public static final String ACCOUNT_LINKED = "PractitionerAccountLinked";
    public static final String ACCOUNT_UNLINKED = "PractitionerAccountUnlinked";

    private PractitionerEvents() {
    }
}
