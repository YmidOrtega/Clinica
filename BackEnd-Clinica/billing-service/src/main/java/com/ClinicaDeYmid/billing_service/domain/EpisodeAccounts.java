package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EpisodeAccounts {

    EpisodeAccount save(EpisodeAccount account);

    Optional<EpisodeAccount> lockByAdmission(UUID admissionUuid);

    Optional<EpisodeAccount> findByAdmissionNumber(String admissionNumber);

    List<EpisodeAccount> findByStatus(AccountStatus.Code status, int limit);
}
