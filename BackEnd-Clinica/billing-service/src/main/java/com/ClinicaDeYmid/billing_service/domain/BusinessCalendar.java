package com.ClinicaDeYmid.billing_service.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public final class BusinessCalendar {

    private static final List<MonthDay> FIXED = List.of(MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(7, 20),
            MonthDay.of(8, 7), MonthDay.of(12, 8), MonthDay.of(12, 25));
    private static final List<MonthDay> MOVED_TO_MONDAY = List.of(MonthDay.of(1, 6), MonthDay.of(3, 19),
            MonthDay.of(6, 29), MonthDay.of(8, 15), MonthDay.of(10, 12), MonthDay.of(11, 1), MonthDay.of(11, 11));
    private static final Map<Integer, Set<LocalDate>> HOLIDAYS = new ConcurrentHashMap<>();

    private BusinessCalendar() {
    }

    public static boolean isBusinessDay(LocalDate day) {
        DayOfWeek weekday = day.getDayOfWeek();
        return weekday != DayOfWeek.SATURDAY && weekday != DayOfWeek.SUNDAY && !isHoliday(day);
    }

    public static boolean isHoliday(LocalDate day) {
        return holidaysOf(day.getYear()).contains(day);
    }

    public static LocalDate plusBusinessDays(LocalDate from, int days) {
        if (days < 0) {
            throw new IllegalArgumentException("Business days are counted forward");
        }
        LocalDate day = from;
        int counted = 0;
        while (counted < days) {
            day = day.plusDays(1);
            if (isBusinessDay(day)) {
                counted++;
            }
        }
        return day;
    }

    public static int businessDaysAfter(LocalDate from, LocalDate until) {
        int counted = 0;
        for (LocalDate day = from.plusDays(1); !day.isAfter(until); day = day.plusDays(1)) {
            if (isBusinessDay(day)) {
                counted++;
            }
        }
        return counted;
    }

    public static Set<LocalDate> holidaysOf(int year) {
        return HOLIDAYS.computeIfAbsent(year, BusinessCalendar::computeHolidays);
    }

    private static Set<LocalDate> computeHolidays(int year) {
        Set<LocalDate> holidays = new TreeSet<>();
        FIXED.forEach(day -> holidays.add(day.atYear(year)));
        MOVED_TO_MONDAY.forEach(day -> holidays.add(nextMonday(day.atYear(year))));
        LocalDate easter = easterSunday(year);
        holidays.add(easter.minusDays(3));
        holidays.add(easter.minusDays(2));
        holidays.add(nextMonday(easter.plusDays(39)));
        holidays.add(nextMonday(easter.plusDays(60)));
        holidays.add(nextMonday(easter.plusDays(68)));
        return Set.copyOf(holidays);
    }

    private static LocalDate nextMonday(LocalDate day) {
        return day.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }
}
