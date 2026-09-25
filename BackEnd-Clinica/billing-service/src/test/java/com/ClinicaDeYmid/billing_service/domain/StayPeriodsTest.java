package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StayPeriodsTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Instant ADMITTED = Instant.parse("2026-09-20T22:00:00Z");

    @Test
    void chargesOneDayForEveryStartedTwentyFourHours() {
        assertThat(periods(segment(StayType.GENERAL_WARD, 0, 50))).containsExactly(run(StayType.GENERAL_WARD, 3));
        assertThat(periods(segment(StayType.GENERAL_WARD, 0, 24))).containsExactly(run(StayType.GENERAL_WARD, 1));
    }

    @Test
    void aShortStayStillCostsOneDay() {
        assertThat(periods(segment(StayType.OBSERVATION, 0, 0.5))).containsExactly(run(StayType.OBSERVATION, 1));
    }

    @Test
    void aTransferGivesEachDayToTheBedThatHeldThePatientTheLongest() {
        List<StayPeriods.Run> runs = StayPeriods.of(List.of(segment(StayType.GENERAL_WARD, 0, 26),
                segment(StayType.ICU_ADULT, 26, 60)), ADMITTED.plus(Duration.ofDays(10)), BOGOTA);

        assertThat(runs).extracting(StayPeriods.Run::stayType, StayPeriods.Run::periods)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(StayType.GENERAL_WARD, 1),
                        org.assertj.core.groups.Tuple.tuple(StayType.ICU_ADULT, 2));
        assertThat(runs.get(1).startsOn()).isEqualTo(LocalDate.parse("2026-09-21"));
    }

    @Test
    void aTieGoesToTheLaterBed() {
        List<StayPeriods.Run> runs = StayPeriods.of(List.of(segment(StayType.GENERAL_WARD, 0, 12),
                segment(StayType.PRIVATE_ROOM, 12, 24)), ADMITTED.plus(Duration.ofDays(10)), BOGOTA);

        assertThat(runs).extracting(StayPeriods.Run::stayType).containsExactly(StayType.PRIVATE_ROOM);
    }

    @Test
    void aWholeDayWithoutABedIsNotCharged() {
        List<StayPeriods.Run> runs = StayPeriods.of(List.of(segment(StayType.GENERAL_WARD, 0, 10),
                segment(StayType.GENERAL_WARD, 50, 60)), ADMITTED.plus(Duration.ofDays(10)), BOGOTA);

        assertThat(runs).extracting(StayPeriods.Run::periods).containsExactly(1, 1);
    }

    @Test
    void anOpenBedCountsUntilTheGivenMoment() {
        StaySegment open = StaySegment.begin(null, UUID.randomUUID(), StayType.GENERAL_WARD, ADMITTED);

        assertThat(StayPeriods.of(List.of(open), ADMITTED.plus(Duration.ofHours(49)), BOGOTA))
                .extracting(StayPeriods.Run::periods).containsExactly(3);
    }

    @Test
    void aBedWithoutTypeStillCountsSoNothingHides() {
        assertThat(periods(segment(null, 0, 30))).containsExactly(run(null, 2));
    }

    private static List<StayPeriods.Run> periods(StaySegment segment) {
        return StayPeriods.of(List.of(segment), ADMITTED.plus(Duration.ofDays(10)), BOGOTA).stream()
                .map(run -> new StayPeriods.Run(run.stayType(), null, null, null, run.periods())).toList();
    }

    private static StayPeriods.Run run(StayType type, int periods) {
        return new StayPeriods.Run(type, null, null, null, periods);
    }

    private static StaySegment segment(StayType type, double fromHours, double toHours) {
        StaySegment segment = StaySegment.begin(null, UUID.randomUUID(), type,
                ADMITTED.plus(Duration.ofMinutes((long) (fromHours * 60))));
        segment.end(ADMITTED.plus(Duration.ofMinutes((long) (toHours * 60))));
        return segment;
    }
}
