package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.application.dian.DianAnswer;
import com.ClinicaDeYmid.billing_service.application.dian.DianReceipt;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.DianEnvironment;
import com.ClinicaDeYmid.billing_service.support.DianSimulator;
import com.ClinicaDeYmid.billing_service.support.LocalDianSigningKey;
import com.ClinicaDeYmid.billing_service.support.StubbedServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoapDianGatewayTest {

    private SoapDianGateway gateway;

    @BeforeEach
    void aDianThatAnswersAtTheSimulator() {
        StubbedServices.reset();
        gateway = new SoapDianGateway(RestClient.create(), new DianSoapEnvelope(LocalDianSigningKey.SHARED,
                Clock.systemUTC()), Map.of(DianEnvironment.TEST, DianSimulator.url(),
                DianEnvironment.PRODUCTION, DianSimulator.url()));
    }

    @Test
    void uploadsTheTestSetAndKeepsTheZipKey() {
        DianSimulator.receivesTheTestSet("f1b2c3d4-zip");

        DianReceipt receipt = gateway.sendTestSet(DianEnvironment.TEST, "z1.zip", new byte[]{1, 2}, "set-1");

        assertThat(receipt.received()).isTrue();
        assertThat(receipt.trackId()).isEqualTo("f1b2c3d4-zip");
        StubbedServices.server().verify(postRequestedFor(urlPathEqualTo(DianSimulator.PATH))
                .withHeader("Content-Type", containing("application/soap+xml"))
                .withRequestBody(containing("<wcf:fileName>z1.zip</wcf:fileName>"))
                .withRequestBody(containing("<wcf:contentFile>AQI=</wcf:contentFile>"))
                .withRequestBody(containing("<wcf:testSetId>set-1</wcf:testSetId>")));
    }

    @Test
    void reportsARefusedUpload() {
        DianSimulator.refusesTheUpload("El set de pruebas no existe");

        DianReceipt receipt = gateway.sendTestSet(DianEnvironment.TEST, "z1.zip", new byte[]{1}, "set-1");

        assertThat(receipt.received()).isFalse();
        assertThat(receipt.errors()).containsExactly("El set de pruebas no existe");
    }

    @Test
    void readsTheValidationOfTheZip() {
        DianSimulator.validates("GetStatusZip");
        DianAnswer accepted = gateway.statusOfZip(DianEnvironment.TEST, "zip");

        DianSimulator.isStillProcessing();
        DianAnswer processing = gateway.statusOfZip(DianEnvironment.TEST, "zip");

        DianSimulator.rejects("GetStatusZip", "Regla: FAD06, Rechazo: CUFE mal calculado", "Regla: ZE02, Rechazo: firma");
        DianAnswer rejected = gateway.statusOfZip(DianEnvironment.TEST, "zip");

        assertThat(accepted.verdict()).isEqualTo(DianAnswer.Verdict.ACCEPTED);
        assertThat(accepted.statusCode()).isEqualTo("00");
        assertThat(accepted.applicationResponse()).isEqualTo(DianSimulator.APPLICATION_RESPONSE);
        assertThat(processing.verdict()).isEqualTo(DianAnswer.Verdict.PROCESSING);
        assertThat(rejected.verdict()).isEqualTo(DianAnswer.Verdict.REJECTED);
        assertThat(rejected.statusCode()).isEqualTo("99");
        assertThat(rejected.errors()).containsExactly("Regla: FAD06, Rechazo: CUFE mal calculado",
                "Regla: ZE02, Rechazo: firma");
        assertThat(rejected.applicationResponse()).isNull();
    }

    @Test
    void sendsTheBillSynchronouslyInProduction() {
        DianSimulator.validates("SendBillSync");

        DianAnswer answer = gateway.sendBill(DianEnvironment.PRODUCTION, "z1.zip", new byte[]{1});

        assertThat(answer.verdict()).isEqualTo(DianAnswer.Verdict.ACCEPTED);
    }

    @Test
    void aFaultOrAnUnreachableDianIsTransient() {
        DianSimulator.isDown();
        assertThatThrownBy(() -> gateway.statusOfDocument(DianEnvironment.TEST, "cufe"))
                .isInstanceOf(BillingException.DianUnavailable.class);

        SoapDianGateway unreachable = new SoapDianGateway(RestClient.create(), new DianSoapEnvelope(
                LocalDianSigningKey.SHARED, Clock.systemUTC()), Map.of(DianEnvironment.TEST, "http://127.0.0.1:9/svc"));
        assertThatThrownBy(() -> unreachable.statusOfDocument(DianEnvironment.TEST, "cufe"))
                .isInstanceOf(BillingException.DianUnavailable.class);
    }
}
