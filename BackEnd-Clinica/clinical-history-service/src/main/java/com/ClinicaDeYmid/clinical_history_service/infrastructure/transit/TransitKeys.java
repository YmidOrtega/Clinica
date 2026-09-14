package com.ClinicaDeYmid.clinical_history_service.infrastructure.transit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class TransitKeys {

    private static final Logger log = LoggerFactory.getLogger(TransitKeys.class);

    private final TransitClient client;
    private final String name;
    private final Duration refreshInterval;
    private final Clock clock;
    private volatile TransitKey current;
    private volatile Instant fetchedAt;

    public TransitKeys(TransitClient client, String name, Duration refreshInterval, Clock clock) {
        this.client = client;
        this.name = name;
        this.refreshInterval = refreshInterval;
        this.clock = clock;
        refresh();
    }

    public TransitClient client() {
        return client;
    }

    public TransitKey current() {
        if (Instant.now(clock).isAfter(fetchedAt.plus(refreshInterval))) {
            try {
                refresh();
            } catch (ClinicalKeysUnavailableException unavailable) {
                log.warn("Using the last known versions of transit key {}: {}", name, unavailable.getMessage());
            }
        }
        return current;
    }

    public TransitKey refresh() {
        TransitKey fetched = client.key(name);
        current = fetched;
        fetchedAt = Instant.now(clock);
        return fetched;
    }

    public TransitKey refreshIfUnknown(KeyVersion version) {
        TransitKey known = current();
        if (known.usable(version) || !version.keyName().equals(name) || version.version() <= known.latestVersion()) {
            return known;
        }
        try {
            return refresh();
        } catch (ClinicalKeysUnavailableException unavailable) {
            return known;
        }
    }
}
