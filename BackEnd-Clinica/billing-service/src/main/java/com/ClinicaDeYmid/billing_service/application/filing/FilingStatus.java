package com.ClinicaDeYmid.billing_service.application.filing;

import com.ClinicaDeYmid.billing_service.domain.FilingDeadline;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceFiling;

public record FilingStatus(Invoice invoice, InvoiceFiling filing, FilingDeadline deadline, String cuv) {
}
