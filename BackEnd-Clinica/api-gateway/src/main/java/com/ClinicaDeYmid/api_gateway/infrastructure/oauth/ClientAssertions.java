package com.ClinicaDeYmid.api_gateway.infrastructure.oauth;

import com.ClinicaDeYmid.commons.openbao.oauth.TransitClientAssertions;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

final class ClientAssertions {

    private static final String JWT_BEARER = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    private final TransitClientAssertions assertions;
    private final String clientId;
    private final String audience;

    ClientAssertions(TransitClientAssertions assertions, String clientId, String audience) {
        this.assertions = assertions;
        this.clientId = clientId;
        this.audience = audience;
    }

    String clientId() {
        return clientId;
    }

    MultiValueMap<String, String> parameters() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("client_assertion_type", JWT_BEARER);
        parameters.add("client_assertion", assertions.assertion(clientId, audience));
        return parameters;
    }
}
