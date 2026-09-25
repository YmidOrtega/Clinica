package com.ClinicaDeYmid.billing_service.application.sale;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccounts;
import com.ClinicaDeYmid.billing_service.domain.Sale;
import com.ClinicaDeYmid.billing_service.domain.Sales;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SaleQueries {

    private final Sales sales;
    private final EpisodeAccounts accounts;

    public SaleQueries(Sales sales, EpisodeAccounts accounts) {
        this.sales = sales;
        this.accounts = accounts;
    }

    public Sale sale(UUID uuid) {
        return sales.findByUuid(uuid).orElseThrow(BillingException.SaleNotFound::new);
    }

    public List<Sale> salesOf(String admissionNumber) {
        EpisodeAccount account = accounts.findByAdmissionNumber(admissionNumber)
                .orElseThrow(BillingException.AccountNotFound::new);
        return sales.findByAccount(account.uuid());
    }
}
