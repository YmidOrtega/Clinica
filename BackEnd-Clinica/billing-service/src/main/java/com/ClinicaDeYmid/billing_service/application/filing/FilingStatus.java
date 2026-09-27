package com.ClinicaDeYmid.billing_service.application.filing;

import com.ClinicaDeYmid.billing_service.domain.BusinessDeadline;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceFiling;

public record FilingStatus(Invoice invoice, InvoiceFiling filing, BusinessDeadline deadline, String cuv) {
}
