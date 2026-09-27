package com.ClinicaDeYmid.billing_service.application.objection;

public record ObjectionPolicy(int warningBusinessDays) {

    public ObjectionPolicy {
        if (warningBusinessDays < 0 || warningBusinessDays > 15) {
            throw new IllegalArgumentException("The warning window goes from 0 to 15 business days");
        }
    }
}
