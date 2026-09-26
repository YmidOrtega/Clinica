package com.ClinicaDeYmid.billing_service.application.filing;

public record FilingPolicy(int warningBusinessDays) {

    public FilingPolicy {
        if (warningBusinessDays < 0 || warningBusinessDays > 22) {
            throw new IllegalArgumentException("The warning window goes from 0 to 22 business days");
        }
    }
}
