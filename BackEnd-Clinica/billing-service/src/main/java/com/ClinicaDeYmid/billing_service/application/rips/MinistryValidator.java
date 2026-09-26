package com.ClinicaDeYmid.billing_service.application.rips;

public interface MinistryValidator {

    String wireFormat(RipsDocument rips);

    MinistryAnswer submit(String ripsJson, String attachedDocument, String providerNit);

    MinistryAnswer recover(String cuv, String providerNit);
}
