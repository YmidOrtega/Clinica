package com.ClinicaDeYmid.commons.security;

@FunctionalInterface
public interface ClientAssertionSigner {

    String assertion(String clientId, String audience);
}
