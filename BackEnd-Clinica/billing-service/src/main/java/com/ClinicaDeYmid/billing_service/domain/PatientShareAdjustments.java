package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.UUID;

public interface PatientShareAdjustments {

    PatientShareAdjustment save(PatientShareAdjustment adjustment);

    List<PatientShareAdjustment> findByAccount(UUID accountUuid);
}
