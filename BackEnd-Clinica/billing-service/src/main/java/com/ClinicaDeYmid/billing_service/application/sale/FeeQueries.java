package com.ClinicaDeYmid.billing_service.application.sale;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.PractitionerFee;
import com.ClinicaDeYmid.billing_service.domain.PractitionerFees;
import com.ClinicaDeYmid.billing_service.domain.Sales;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FeeQueries {

    public static final int MAXIMUM_PAGE = 200;

    private final PractitionerFees fees;
    private final Sales sales;

    public FeeQueries(PractitionerFees fees, Sales sales) {
        this.fees = fees;
        this.sales = sales;
    }

    public List<PractitionerFee> ofSale(UUID saleUuid) {
        sales.findByUuid(saleUuid).orElseThrow(BillingException.SaleNotFound::new);
        return fees.findBySale(saleUuid);
    }

    public List<PractitionerFee> ofPractitioner(UUID practitionerUuid, PractitionerFee.Status status, int limit) {
        return fees.findByPractitioner(practitionerUuid, status, Math.clamp(limit, 1, MAXIMUM_PAGE));
    }
}
