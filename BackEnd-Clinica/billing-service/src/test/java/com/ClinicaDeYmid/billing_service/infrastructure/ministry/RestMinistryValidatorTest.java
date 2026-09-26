package com.ClinicaDeYmid.billing_service.infrastructure.ministry;

import com.ClinicaDeYmid.billing_service.application.rips.MinistryAnswer;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.support.MinistrySimulator;
import com.ClinicaDeYmid.billing_service.support.StubbedServices;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestMinistryValidatorTest {

    private static final String LOGIN = MinistrySimulator.PATH + RestMinistryValidator.LOGIN;
    private static final String SUBMIT = MinistrySimulator.PATH + RestMinistryValidator.SUBMIT;
    private static final MinistryCredentials CREDENTIALS = new MinistryCredentials("CC", "80100200", "clave");

    private RestMinistryValidator validator;

    @BeforeEach
    void aValidatorAtTheSimulator() {
        StubbedServices.reset();
        validator = validator(CREDENTIALS);
    }

    @Test
    void logsInOnceAndReadsTheValidationWhateverTheCaseOfItsFields() {
        MinistrySimulator.logsIn();
        MinistrySimulator.validates("SETP990000001");

        MinistryAnswer first = validator.submit("{\"numFactura\":\"SETP990000001\"}", "PEF0dGFjaGVkLz4=", "800197268");
        validator.submit("{\"numFactura\":\"SETP990000001\"}", "PEF0dGFjaGVkLz4=", "800197268");

        assertThat(first.validated()).isTrue();
        assertThat(first.cuv()).isEqualTo(MinistrySimulator.CUV);
        assertThat(first.processId()).isEqualTo(1024L);
        assertThat(first.filedAt()).isEqualTo(Instant.parse("2026-09-26T17:25:54.770516200Z"));
        assertThat(first.findings()).singleElement().satisfies(finding -> assertThat(finding.rejects()).isFalse());
        StubbedServices.server().verify(1, postRequestedFor(urlPathEqualTo(LOGIN))
                .withRequestBody(equalToJson("""
                        {"persona":{"identificacion":{"tipo":"CC","numero":"80100200"}},"clave":"clave",
                         "nit":"800197268"}""")));
        StubbedServices.server().verify(2, postRequestedFor(urlPathEqualTo(SUBMIT))
                .withRequestBody(equalToJson("""
                        {"rips":{"numFactura":"SETP990000001"},"xmlFevFile":"PEF0dGFjaGVkLz4="}""")));
    }

    @Test
    void logsInAgainWhenTheTokenExpired() {
        MinistrySimulator.logsIn();
        StubbedServices.server().stubFor(WireMock.post(urlPathEqualTo(SUBMIT)).inScenario("token")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson("""
                        {"ResultState":false,"ResultadosValidacion":[{"Clase":"RECHAZO","Codigo":"TOT002",
                          "Descripcion":"Hubo un error en la petición"}]}"""))
                .willSetStateTo("renewed"));
        StubbedServices.server().stubFor(WireMock.post(urlPathEqualTo(SUBMIT)).inScenario("token")
                .whenScenarioStateIs("renewed")
                .withHeader("Authorization", equalTo("Bearer " + MinistrySimulator.TOKEN))
                .willReturn(okJson("""
                        {"ResultState":true,"NumFactura":"F1","CodigoUnicoValidacion":"%s"}"""
                        .formatted(MinistrySimulator.CUV))));

        assertThat(validator.submit("{}", "", "800197268").validated()).isTrue();
        StubbedServices.server().verify(2, postRequestedFor(urlPathEqualTo(LOGIN)));
    }

    @Test
    void anAnswerWithoutAValidationResultMeansTheValidatorIsUnavailable() {
        MinistrySimulator.logsIn();
        StubbedServices.server().stubFor(WireMock.post(urlPathEqualTo(SUBMIT))
                .willReturn(WireMock.aResponse().withStatus(502).withBody("<html>Bad gateway</html>")));

        assertThatThrownBy(() -> validator.submit("{}", "", "800197268"))
                .isInstanceOf(BillingException.MinistryUnavailable.class);
    }

    @Test
    void refusesToLogInWithoutCredentials() {
        assertThatThrownBy(() -> validator(new MinistryCredentials("CC", "", null)).submit("{}", "", "800197268"))
                .isInstanceOf(BillingException.MinistryCredentialsMissing.class);
        assertThat(CREDENTIALS.toString()).doesNotContain("clave");
    }

    private static RestMinistryValidator validator(MinistryCredentials credentials) {
        return new RestMinistryValidator(RestClient.builder().baseUrl(MinistrySimulator.url()).build(), credentials,
                Clock.systemUTC());
    }
}
