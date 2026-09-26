package com.ClinicaDeYmid.billing_service.application.dian;

import com.ClinicaDeYmid.billing_service.domain.BillingException;

public record DianSoftware(String softwareId, String pin, String testSetId) {

    public DianSoftware requireConfigured() {
        if (softwareId == null || softwareId.isBlank() || pin == null || pin.isBlank()) {
            throw new BillingException.DianSoftwareNotConfigured();
        }
        return this;
    }

    public String requireTestSetId() {
        if (testSetId == null || testSetId.isBlank()) {
            throw new BillingException.DianTestSetNotConfigured();
        }
        return testSetId;
    }
}
