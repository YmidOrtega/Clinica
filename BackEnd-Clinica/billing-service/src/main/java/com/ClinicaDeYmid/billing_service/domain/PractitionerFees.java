package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.UUID;

public interface PractitionerFees {

    List<PractitionerFee> saveAll(List<PractitionerFee> fees);

    List<PractitionerFee> findBySale(UUID saleUuid);

    List<PractitionerFee> findByPractitioner(UUID practitionerUuid, PractitionerFee.Status status, int limit);
}
