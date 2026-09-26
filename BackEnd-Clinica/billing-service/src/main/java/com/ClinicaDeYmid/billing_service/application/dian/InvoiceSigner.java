package com.ClinicaDeYmid.billing_service.application.dian;

import java.time.Instant;

public interface InvoiceSigner {

    String sign(String unsignedUbl, Instant signingTime);
}
