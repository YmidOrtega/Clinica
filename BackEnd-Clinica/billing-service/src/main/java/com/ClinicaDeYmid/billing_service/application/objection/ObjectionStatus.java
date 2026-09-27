package com.ClinicaDeYmid.billing_service.application.objection;

import com.ClinicaDeYmid.billing_service.domain.BusinessDeadline;
import com.ClinicaDeYmid.billing_service.domain.PayerObjection;

public record ObjectionStatus(PayerObjection objection, BusinessDeadline responseDue) {
}
