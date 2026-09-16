package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.contracting_service.domain.Contract;
import com.ClinicaDeYmid.contracting_service.domain.TariffManualVersion;

public interface ContractingEventOutbox {

    void contractChanged(Contract contract, String change);

    void tariffVersionChanged(TariffManualVersion version, String change);
}
