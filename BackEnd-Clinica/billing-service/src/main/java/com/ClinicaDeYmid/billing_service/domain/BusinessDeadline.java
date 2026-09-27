package com.ClinicaDeYmid.billing_service.domain;

import java.time.LocalDate;

public record BusinessDeadline(LocalDate start, LocalDate deadline, int remainingBusinessDays, State state) {

    public enum State {
        ON_TIME,
        DUE_SOON,
        OVERDUE
    }

    public static BusinessDeadline of(LocalDate start, int businessDays, LocalDate today, int warningDays) {
        LocalDate deadline = BusinessCalendar.plusBusinessDays(DomainRules.required(start, "start"), businessDays);
        if (today.isAfter(deadline)) {
            return new BusinessDeadline(start, deadline, -BusinessCalendar.businessDaysAfter(deadline, today),
                    State.OVERDUE);
        }
        int remaining = BusinessCalendar.businessDaysAfter(today, deadline);
        return new BusinessDeadline(start, deadline, remaining, remaining <= warningDays ? State.DUE_SOON : State.ON_TIME);
    }
}
