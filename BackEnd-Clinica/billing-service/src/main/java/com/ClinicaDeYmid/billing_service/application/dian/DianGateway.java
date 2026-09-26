package com.ClinicaDeYmid.billing_service.application.dian;

import com.ClinicaDeYmid.billing_service.domain.DianEnvironment;

public interface DianGateway {

    DianReceipt sendTestSet(DianEnvironment environment, String zipName, byte[] zip, String testSetId);

    DianAnswer sendBill(DianEnvironment environment, String zipName, byte[] zip);

    DianAnswer statusOfZip(DianEnvironment environment, String trackId);

    DianAnswer statusOfDocument(DianEnvironment environment, String cufe);
}
