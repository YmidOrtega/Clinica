package com.ClinicaDeYmid.billing_service.application.objection;

import com.ClinicaDeYmid.billing_service.domain.BusinessDeadline;
import com.ClinicaDeYmid.billing_service.domain.PayerObjection;

public interface ObjectionAlertOutbox {

    boolean alertOnce(PayerObjection objection, BusinessDeadline responseDue);
}
