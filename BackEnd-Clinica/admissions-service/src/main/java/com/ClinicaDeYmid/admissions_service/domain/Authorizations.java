package com.ClinicaDeYmid.admissions_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Authorizations {

    Authorization save(Authorization authorization);

    Optional<Authorization> findByUuid(UUID uuid);

    Optional<Authorization> findByAdmissionAndNumber(UUID admissionUuid, String number);

    List<Authorization> findByAdmission(UUID admissionUuid);
}
