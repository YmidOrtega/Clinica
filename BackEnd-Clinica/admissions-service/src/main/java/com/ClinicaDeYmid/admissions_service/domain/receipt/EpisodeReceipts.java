package com.ClinicaDeYmid.admissions_service.domain.receipt;

import java.util.Optional;
import java.util.UUID;

public interface EpisodeReceipts {

    void add(EpisodeReceipt receipt);

    Optional<EpisodeReceipt> find(UUID id);

    Optional<EpisodeReceipt> findByNumberAndFingerprint(String admissionNumber, String sha256);
}
