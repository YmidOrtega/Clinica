package com.ClinicaDeYmid.billing_service.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StayPeriods {

    public static final Duration PERIOD = Duration.ofHours(24);

    private StayPeriods() {
    }

    public record Run(StayType stayType, LocalDate startsOn, Instant from, Instant to, int periods) {
    }

    public static List<Run> of(List<StaySegment> segments, Instant until, ZoneId zone) {
        List<StaySegment> usable = segments.stream()
                .filter(segment -> segment.startedAt().isBefore(endOf(segment, until)))
                .sorted(Comparator.comparing(StaySegment::startedAt))
                .toList();
        if (usable.isEmpty()) {
            return List.of();
        }
        Instant anchor = usable.getFirst().startedAt();
        Instant end = usable.stream().map(segment -> endOf(segment, until)).max(Comparator.naturalOrder()).orElseThrow();
        List<Run> runs = new ArrayList<>();
        Run current = null;
        for (Instant start = anchor; start.isBefore(end); start = start.plus(PERIOD)) {
            Instant finish = start.plus(PERIOD);
            Optional<Optional<StayType>> charged = dominant(usable, start, finish, until);
            if (charged.isEmpty()) {
                if (current != null) {
                    runs.add(current);
                    current = null;
                }
                continue;
            }
            StayType type = charged.get().orElse(null);
            if (current != null && current.stayType() == type && current.to().equals(start)) {
                current = new Run(type, current.startsOn(), current.from(), finish, current.periods() + 1);
            } else {
                if (current != null) {
                    runs.add(current);
                }
                current = new Run(type, LocalDate.ofInstant(start, zone), start, finish, 1);
            }
        }
        if (current != null) {
            runs.add(current);
        }
        return runs;
    }

    private static Optional<Optional<StayType>> dominant(List<StaySegment> segments, Instant start, Instant finish,
                                                         Instant until) {
        Map<Optional<StayType>, Duration> occupied = new LinkedHashMap<>();
        Map<Optional<StayType>, Instant> latest = new LinkedHashMap<>();
        for (StaySegment segment : segments) {
            Instant from = max(segment.startedAt(), start);
            Instant to = min(endOf(segment, until), finish);
            if (!from.isBefore(to)) {
                continue;
            }
            Optional<StayType> type = Optional.ofNullable(segment.stayType());
            occupied.merge(type, Duration.between(from, to), Duration::plus);
            latest.merge(type, from, (left, right) -> left.isAfter(right) ? left : right);
        }
        return occupied.keySet().stream()
                .max(Comparator.<Optional<StayType>, Duration>comparing(occupied::get).thenComparing(latest::get));
    }

    private static Instant endOf(StaySegment segment, Instant until) {
        return segment.endedAt() == null ? until : segment.endedAt();
    }

    private static Instant max(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private static Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }
}
