package com.ClinicaDeYmid.commons.openbao.transit;

import com.ClinicaDeYmid.commons.openbao.testing.OpenBaoTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransitClientIT {

    private static final byte[] DATA_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PATIENT_A = "patient-a".getBytes(StandardCharsets.US_ASCII);

    private final TransitClient transit = new TransitClient(OpenBaoTestContainer.template(), "transit");

    @Test
    void encryptionIsBoundToItsAssociatedDataAndKeyVersion() {
        String key = OpenBaoTestContainer.createKey("kek", "aes256-gcm96");
        KeyVersion v1 = new KeyVersion(key, 1);

        byte[] wrapped = transit.encrypt(v1, DATA_KEY, PATIENT_A);

        assertThat(new String(wrapped, StandardCharsets.US_ASCII)).startsWith("vault:v1:");
        assertThat(transit.decrypt(v1, wrapped, PATIENT_A)).isEqualTo(DATA_KEY);
        assertThatThrownBy(() -> transit.decrypt(v1, wrapped, "patient-b".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(TransitRejectedException.class);
        assertThatThrownBy(() -> transit.decrypt(new KeyVersion(key, 2), wrapped, PATIENT_A)).isInstanceOf(TransitRejectedException.class);
    }

    @Test
    void signsInDerOrJwsFormatWithAChosenKeyVersion() throws Exception {
        String key = OpenBaoTestContainer.createKey("signer", "ecdsa-p256");
        OpenBaoTestContainer.rotate(key);
        TransitKey described = transit.key(key);
        PublicKey v1 = publicKey(described.publicKeysPem().get(1));
        byte[] input = "clinica".getBytes(StandardCharsets.US_ASCII);

        byte[] der = transit.sign(new KeyVersion(key, 1), input, SignatureFormat.DER);
        byte[] jws = transit.sign(new KeyVersion(key, 1), input, SignatureFormat.JWS);

        Signature derVerifier = Signature.getInstance("SHA256withECDSA");
        derVerifier.initVerify(v1);
        derVerifier.update(input);
        Signature jwsVerifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        jwsVerifier.initVerify(v1);
        jwsVerifier.update(input);
        assertThat(derVerifier.verify(der)).isTrue();
        assertThat(jws).hasSize(64);
        assertThat(jwsVerifier.verify(jws)).isTrue();
    }

    @Test
    void describesTheVersionsAndPublicKeysOfAKey() {
        String key = OpenBaoTestContainer.createKey("seal", "ecdsa-p256");
        OpenBaoTestContainer.rotate(key);

        TransitKey described = transit.key(key);

        assertThat(described.type()).isEqualTo("ecdsa-p256");
        assertThat(described.latest().id()).isEqualTo(key + "-v2");
        assertThat(described.usableVersions()).containsOnlyKeys(key + "-v1", key + "-v2");
        assertThat(described.publicKeysPem().get(1)).startsWith("-----BEGIN PUBLIC KEY-----");
    }

    @Test
    void anUnreachableOrMissingKeyServiceIsReportedAsUnavailable() {
        TransitClient unreachable = new TransitClient(new VaultTemplate(VaultEndpoint.from(URI.create("http://127.0.0.1:1")),
                new TokenAuthentication("root")), "transit");

        assertThatThrownBy(() -> unreachable.key("any-key")).isInstanceOf(OpenBaoUnavailableException.class);
        assertThatThrownBy(() -> transit.key("does-not-exist")).isInstanceOf(OpenBaoUnavailableException.class);
    }

    private static PublicKey publicKey(String pem) throws Exception {
        byte[] der = Base64.getMimeDecoder().decode(pem.replaceAll("-----(BEGIN|END) PUBLIC KEY-----", ""));
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
    }

    @Test
    void parsesOnlyVersionedTransitKeyIds() {
        assertThat(KeyVersion.parse("auth-jwt-v12")).contains(new KeyVersion("auth-jwt", 12));
        assertThat(KeyVersion.parse("seal-dev")).isEmpty();
        assertThat(KeyVersion.parse("auth-jwt-v0")).isEmpty();
        assertThat(KeyVersion.parse("../x-v1")).isEmpty();
    }
}
