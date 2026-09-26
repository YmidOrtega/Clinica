package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.UUID;

public interface DianVerdicts {

    DianVerdict save(DianVerdict verdict);

    List<DianVerdict> ofInvoice(UUID invoiceUuid);
}
