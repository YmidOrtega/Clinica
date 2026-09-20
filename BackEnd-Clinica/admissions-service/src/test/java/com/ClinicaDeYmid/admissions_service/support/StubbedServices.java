package com.ClinicaDeYmid.admissions_service.support;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public final class StubbedServices {

    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        SERVER.start();
    }

    private StubbedServices() {
    }

    public static WireMockServer server() {
        return SERVER;
    }

    public static String baseUrl() {
        return SERVER.baseUrl();
    }

    public static void reset() {
        SERVER.resetAll();
    }
}
