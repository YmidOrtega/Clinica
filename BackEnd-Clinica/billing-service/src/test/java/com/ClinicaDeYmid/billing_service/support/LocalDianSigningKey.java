package com.ClinicaDeYmid.billing_service.support;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.infrastructure.dian.DianSigningKey;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class LocalDianSigningKey implements DianSigningKey {

    public static final LocalDianSigningKey SHARED = new LocalDianSigningKey();

    private final KeyPair keys = TestCertificates.rsaKeyPair();
    private final X509Certificate certificate = TestCertificates.selfSigned(keys, "Clinica de Ymid Pruebas",
            Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(365)));
    private volatile boolean available = true;

    public void available(boolean available) {
        this.available = available;
    }

    public X509Certificate certificate() {
        return certificate;
    }

    @Override
    public List<X509Certificate> certificateChain() {
        if (!available) {
            throw new BillingException.DianSignatureUnavailable();
        }
        return List.of(certificate);
    }

    @Override
    public byte[] signSha256WithRsa(byte[] data) {
        if (!available) {
            throw new BillingException.DianSignatureUnavailable();
        }
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keys.getPrivate());
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException broken) {
            throw new IllegalStateException(broken);
        }
    }
}
