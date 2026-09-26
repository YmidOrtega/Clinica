package com.ClinicaDeYmid.billing_service.application.context;

import java.util.UUID;

public interface ContractDirectory {

    ContractLookup contract(UUID contractUuid);
}
