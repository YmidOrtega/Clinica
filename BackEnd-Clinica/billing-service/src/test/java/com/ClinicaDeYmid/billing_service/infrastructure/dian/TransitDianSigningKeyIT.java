package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.support.TestCertificates;
import com.ClinicaDeYmid.billing_service.support.XadesVerification;
import com.ClinicaDeYmid.commons.openbao.testing.OpenBaoTestContainer;
import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransitDianSigningKeyIT {

    private static final TransitClient TRANSIT = new TransitClient(OpenBaoTestContainer.template(), "transit");

    @Test
    void signsTheInvoiceWithTheImportedKeyThatNeverLeavesOpenBao() throws Exception {
        KeyPair keys = TestCertificates.rsaKeyPair();
        X509Certificate certificate = certificate(keys);
        String path = provision(keys, certificate);

        String signed = new XadesInvoiceSigner(key(path), ZoneId.of("America/Bogota"))
                .sign(StaxUblWriterTest.sampleUbl(), Instant.now());

        XadesVerification.Result result = XadesVerification.verify(signed, certificate.getPublicKey());
        assertThat(result.valid()).isTrue();
        assertThat(TRANSIT.key(path.substring(path.lastIndexOf('/') + 1)).publicKeysPem()).hasSize(1);
    }

    @Test
    void refusesACertificateThatDoesNotBelongToTheKey() {
        KeyPair keys = TestCertificates.rsaKeyPair();
        String path = provision(keys, certificate(TestCertificates.rsaKeyPair()));

        assertThatThrownBy(() -> key(path).certificateChain())
                .isInstanceOf(BillingException.DianCertificateInvalid.class)
                .hasMessageContaining("no corresponde");
    }

    @Test
    void reportsTheSignatureAsUnavailableWhenTheKeyIsMissing() {
        TransitDianSigningKey missing = new TransitDianSigningKey(TRANSIT, OpenBaoTestContainer.template(),
                "missing-" + UUID.randomUUID(), "secret", "billing/dian/none", Duration.ofMinutes(5), Clock.systemUTC());

        assertThatThrownBy(missing::certificateChain).isInstanceOf(BillingException.DianSignatureUnavailable.class);
    }

    private static X509Certificate certificate(KeyPair keys) {
        return TestCertificates.selfSigned(keys, "Clinica de Ymid Habilitacion", Instant.now().minus(Duration.ofDays(1)),
                Instant.now().plus(Duration.ofDays(30)));
    }

    private static String provision(KeyPair keys, X509Certificate certificate) {
        String name = "billing-dian-" + UUID.randomUUID().toString().substring(0, 8);
        OpenBaoTestContainer.importRsaKey(name, keys.getPrivate());
        OpenBaoTestContainer.putSecret("billing/dian/" + name, Map.of("pem", TestCertificates.pem(certificate)));
        return "billing/dian/" + name;
    }

    private static TransitDianSigningKey key(String path) {
        return new TransitDianSigningKey(TRANSIT, OpenBaoTestContainer.template(), path.substring(path.lastIndexOf('/') + 1),
                "secret", path, Duration.ofMinutes(5), Clock.systemUTC());
    }
}
