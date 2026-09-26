package com.ClinicaDeYmid.billing_service.application.filing;

import com.ClinicaDeYmid.billing_service.domain.FilingDeadline;
import com.ClinicaDeYmid.billing_service.domain.Invoice;

public interface FilingAlertOutbox {

    boolean alertOnce(Invoice invoice, FilingDeadline deadline, String cuv);
}
