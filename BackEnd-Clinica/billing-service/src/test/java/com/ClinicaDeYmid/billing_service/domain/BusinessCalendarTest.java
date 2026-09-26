package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessCalendarTest {

    @Test
    void knowsTheEighteenColombianHolidaysOf2026() {
        assertThat(BusinessCalendar.easterSunday(2026)).isEqualTo(LocalDate.parse("2026-04-05"));
        assertThat(BusinessCalendar.holidaysOf(2026)).containsExactlyInAnyOrder(
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-12"), LocalDate.parse("2026-03-23"),
                LocalDate.parse("2026-04-02"), LocalDate.parse("2026-04-03"), LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-18"), LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-15"),
                LocalDate.parse("2026-06-29"), LocalDate.parse("2026-07-20"), LocalDate.parse("2026-08-07"),
                LocalDate.parse("2026-08-17"), LocalDate.parse("2026-10-12"), LocalDate.parse("2026-11-02"),
                LocalDate.parse("2026-11-16"), LocalDate.parse("2026-12-08"), LocalDate.parse("2026-12-25"));
    }

    @Test
    void countsBusinessDaysSkippingWeekendsAndHolidays() {
        LocalDate issued = LocalDate.parse("2026-10-01");

        LocalDate deadline = BusinessCalendar.plusBusinessDays(issued, 22);

        assertThat(deadline).isEqualTo(LocalDate.parse("2026-11-04"));
        assertThat(BusinessCalendar.businessDaysAfter(issued, deadline)).isEqualTo(22);
        assertThat(BusinessCalendar.businessDaysAfter(deadline, issued)).isZero();
        assertThat(BusinessCalendar.isBusinessDay(LocalDate.parse("2026-10-12"))).isFalse();
        assertThat(BusinessCalendar.isBusinessDay(LocalDate.parse("2026-10-17"))).isFalse();
        assertThat(BusinessCalendar.isBusinessDay(LocalDate.parse("2026-10-13"))).isTrue();
        assertThat(BusinessCalendar.easterSunday(2027)).isEqualTo(LocalDate.parse("2027-03-28"));
        assertThatThrownBy(() -> BusinessCalendar.plusBusinessDays(issued, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
