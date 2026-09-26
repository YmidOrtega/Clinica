package com.ClinicaDeYmid.billing_service.domain;

public interface CreditNoteCounters {

    long next(String prefix);
}
