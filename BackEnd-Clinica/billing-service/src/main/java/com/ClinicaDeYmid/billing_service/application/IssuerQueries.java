package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.Issuers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IssuerQueries {

    private final Issuers issuers;

    public IssuerQueries(Issuers issuers) {
        this.issuers = issuers;
    }

    public Issuer issuer() {
        return issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new);
    }
}
