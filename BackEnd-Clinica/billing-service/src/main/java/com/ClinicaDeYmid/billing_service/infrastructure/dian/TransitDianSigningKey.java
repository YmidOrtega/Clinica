package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.commons.openbao.transit.KeyVersion;
import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKey;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class TransitDianSigningKey implements DianSigningKey {

    private final TransitClient transit;
    private final VaultOperations vault;
    private final String keyName;
    private final String kvMount;
    private final String certificatePath;
    private final Duration refreshInterval;
    private final Clock clock;
    private volatile Loaded loaded;

    public TransitDianSigningKey(TransitClient transit, VaultOperations vault, String keyName, String kvMount,
                                 String certificatePath, Duration refreshInterval, Clock clock) {
        this.transit = transit;
        this.vault = vault;
        this.keyName = keyName;
        this.kvMount = kvMount;
        this.certificatePath = certificatePath;
        this.refreshInterval = refreshInterval;
        this.clock = clock;
    }

    @Override
    public List<X509Certificate> certificateChain() {
        return current().chain();
    }

    @Override
    public byte[] signSha256WithRsa(byte[] data) {
        KeyVersion version = current().version();
        try {
            return transit.signPkcs1v15(version, data);
        } catch (RuntimeException unavailable) {
            throw new BillingException.DianSignatureUnavailable();
        }
    }

    private Loaded current() {
        Loaded known = loaded;
        if (known == null || Instant.now(clock).isAfter(known.at().plus(refreshInterval))) {
            known = load();
            loaded = known;
        }
        return known;
    }

    private Loaded load() {
        TransitKey key;
        VaultResponse secret;
        try {
            key = transit.key(keyName);
            secret = vault.opsForKeyValue(kvMount, KeyValueBackend.KV_2).get(certificatePath);
        } catch (RuntimeException unavailable) {
            throw new BillingException.DianSignatureUnavailable();
        }
        if (!key.type().startsWith("rsa-")) {
            throw new BillingException.DianCertificateInvalid("La clave de firma DIAN no es RSA");
        }
        if (secret == null || secret.getData() == null || !(secret.getData().get("pem") instanceof String pem)) {
            throw new BillingException.DianCertificateInvalid("No hay certificado de firma DIAN en OpenBao");
        }
        List<X509Certificate> chain = parse(pem);
        String publicKey = key.publicKeysPem().get(key.latestVersion());
        if (publicKey == null || !Arrays.equals(der(publicKey), chain.getFirst().getPublicKey().getEncoded())) {
            throw new BillingException.DianCertificateInvalid(
                    "El certificado DIAN no corresponde a la versión vigente de la clave de firma");
        }
        return new Loaded(new KeyVersion(keyName, key.latestVersion()), chain, Instant.now(clock));
    }

    private static List<X509Certificate> parse(String pem) {
        try {
            List<X509Certificate> chain = CertificateFactory.getInstance("X.509")
                    .generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)))
                    .stream().map(X509Certificate.class::cast).toList();
            if (chain.isEmpty()) {
                throw new BillingException.DianCertificateInvalid("El certificado de firma DIAN está vacío");
            }
            return chain;
        } catch (CertificateException unreadable) {
            throw new BillingException.DianCertificateInvalid("El certificado de firma DIAN no se puede leer");
        }
    }

    private static byte[] der(String pem) {
        return Base64.getDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
    }

    private record Loaded(KeyVersion version, List<X509Certificate> chain, Instant at) {
    }
}
