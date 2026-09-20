package com.ClinicaDeYmid.admissions_service.support;

import java.util.concurrent.atomic.AtomicInteger;

public final class TestSequence {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestSequence() {
    }

    public static int next() {
        return COUNTER.incrementAndGet();
    }
}
