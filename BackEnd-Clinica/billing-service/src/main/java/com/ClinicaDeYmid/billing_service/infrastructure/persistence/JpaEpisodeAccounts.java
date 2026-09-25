package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.AccountStatus;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccounts;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaEpisodeAccounts implements EpisodeAccounts {

    private final EpisodeAccountJpaRepository repository;

    JpaEpisodeAccounts(EpisodeAccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public EpisodeAccount save(EpisodeAccount account) {
        return repository.saveAndFlush(account);
    }

    @Override
    public Optional<EpisodeAccount> lockByAdmission(UUID admissionUuid) {
        return repository.lockByAdmissionUuid(admissionUuid);
    }

    @Override
    public Optional<EpisodeAccount> findByAdmissionNumber(String admissionNumber) {
        return repository.findByAdmissionNumber(admissionNumber);
    }

    @Override
    public List<EpisodeAccount> findByStatus(AccountStatus.Code status, int limit) {
        return repository.findByStatus(status, PageRequest.of(0, limit));
    }
}
