package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.domain.AccountStatus;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccounts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AccountQueries {

    public static final int MAXIMUM_PAGE = 200;

    private final EpisodeAccounts accounts;

    public AccountQueries(EpisodeAccounts accounts) {
        this.accounts = accounts;
    }

    public EpisodeAccount byAdmissionNumber(String admissionNumber) {
        return accounts.findByAdmissionNumber(admissionNumber).orElseThrow(BillingException.AccountNotFound::new);
    }

    public List<EpisodeAccount> inStatus(AccountStatus.Code status, int limit) {
        return accounts.findByStatus(status, Math.clamp(limit, 1, MAXIMUM_PAGE));
    }
}
