package com.ClinicaDeYmid.billing_service.application.context;

import java.util.UUID;

public interface PayerDirectory {

    PayerLookup payer(UUID payerUuid);
}
