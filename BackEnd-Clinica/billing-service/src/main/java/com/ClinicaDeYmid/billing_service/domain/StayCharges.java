package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Optional;

public interface StayCharges {

    StayCharge save(StayCharge charge);

    Optional<StayCharge> find(StayType stayType);

    List<StayCharge> findAll();
}
