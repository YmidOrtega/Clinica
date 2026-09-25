package com.ClinicaDeYmid.billing_service.domain;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface NumberingCounters {

    void open(NumberingResolution resolution);

    long lockNext(UUID resolutionUuid);

    void advance(UUID resolutionUuid);

    Map<UUID, Long> nextNumbers(Collection<UUID> resolutionUuids);
}
