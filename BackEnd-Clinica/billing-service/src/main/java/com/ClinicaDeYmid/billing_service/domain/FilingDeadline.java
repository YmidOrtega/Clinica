package com.ClinicaDeYmid.billing_service.domain;

import java.time.LocalDate;

public record FilingDeadline(LocalDate issuedOn, LocalDate deadline, int remainingBusinessDays, State state) {

    public static final int BUSINESS_DAYS_TO_FILE = 22;

    public enum State {
        ON_TIME,
        DUE_SOON,
        OVERDUE
    }

    public static LocalDate deadlineOf(LocalDate issuedOn) {
        return BusinessCalendar.plusBusinessDays(DomainRules.required(issuedOn, "issuedOn"), BUSINESS_DAYS_TO_FILE);
    }

    public static FilingDeadline of(LocalDate issuedOn, LocalDate today, int warningDays) {
        LocalDate deadline = deadlineOf(issuedOn);
        if (today.isAfter(deadline)) {
            return new FilingDeadline(issuedOn, deadline, -BusinessCalendar.businessDaysAfter(deadline, today),
                    State.OVERDUE);
        }
        int remaining = BusinessCalendar.businessDaysAfter(today, deadline);
        return new FilingDeadline(issuedOn, deadline, remaining, remaining <= warningDays ? State.DUE_SOON : State.ON_TIME);
    }
}
