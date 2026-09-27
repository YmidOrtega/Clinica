package com.ClinicaDeYmid.billing_service.application.filing;

import com.ClinicaDeYmid.billing_service.domain.BusinessDeadline;
import com.ClinicaDeYmid.billing_service.domain.Invoice;

public interface FilingAlertOutbox {

    boolean alertOnce(Invoice invoice, BusinessDeadline deadline, String cuv);
}
