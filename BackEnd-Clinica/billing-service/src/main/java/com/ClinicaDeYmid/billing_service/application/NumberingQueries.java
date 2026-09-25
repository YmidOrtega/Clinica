package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.NumberingCounters;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolution;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolutions;
import com.ClinicaDeYmid.billing_service.domain.ResolutionAlert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NumberingQueries {

    private final NumberingResolutions resolutions;
    private final NumberingCounters counters;
    private final Clock clock;

    public NumberingQueries(NumberingResolutions resolutions, NumberingCounters counters, Clock clock) {
        this.resolutions = resolutions;
        this.counters = counters;
        this.clock = clock;
    }

    public ResolutionState resolution(UUID uuid) {
        NumberingResolution resolution = resolutions.findByUuid(uuid)
                .orElseThrow(BillingException.ResolutionNotFound::new);
        return stateOf(List.of(resolution)).getFirst();
    }

    public List<ResolutionState> resolutions() {
        return stateOf(resolutions.findAll());
    }

    private List<ResolutionState> stateOf(List<NumberingResolution> found) {
        Map<UUID, Long> next = counters.nextNumbers(found.stream().map(NumberingResolution::uuid).toList());
        return found.stream().map(resolution -> {
            long nextNumber = next.getOrDefault(resolution.uuid(), resolution.terms().rangeFrom());
            return new ResolutionState(resolution, nextNumber, resolution.remaining(nextNumber),
                    resolution.alerts(nextNumber, clock));
        }).toList();
    }

    public record ResolutionState(NumberingResolution resolution, long nextNumber, long remaining,
                                  Set<ResolutionAlert> alerts) {
    }
}
