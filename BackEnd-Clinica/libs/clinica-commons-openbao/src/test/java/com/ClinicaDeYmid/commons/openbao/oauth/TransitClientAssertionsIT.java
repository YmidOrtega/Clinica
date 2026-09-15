package com.ClinicaDeYmid.commons.openbao.oauth;

import com.ClinicaDeYmid.commons.openbao.testing.OpenBaoTestContainer;
import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TransitClientAssertionsIT {

    @Test
    void signsAShortLivedPrivateKeyJwtAssertionThatVerifiesWithTheTransitPublicKey() throws Exception {
        String key = OpenBaoTestContainer.createKey("client-assertion", "ecdsa-p256");
        TransitKeys keys = new TransitKeys(new TransitClient(OpenBaoTestContainer.template(), "transit"), key, Duration.ofMinutes(5), Clock.systemUTC());

        String assertion = new TransitClientAssertions(keys, Clock.systemUTC()).assertion("api-gateway", "http://auth.clinica.test");

        String[] parts = assertion.split("\\.");
        JsonNode header = json(parts[0]);
        JsonNode claims = json(parts[1]);
        assertThat(header.get("alg").asText()).isEqualTo("ES256");
        assertThat(claims.get("iss").asText()).isEqualTo("api-gateway");
        assertThat(claims.get("sub").asText()).isEqualTo("api-gateway");
        assertThat(claims.get("aud").asText()).isEqualTo("http://auth.clinica.test");
        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(60);
        assertThat(claims.get("jti").asText()).isNotBlank();
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(publicKey(keys.current().publicKeysPem().get(1)));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    private static JsonNode json(String part) throws Exception {
        return new ObjectMapper().readTree(Base64.getUrlDecoder().decode(part));
    }

    private static PublicKey publicKey(String pem) throws Exception {
        String body = pem.replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
    }
}
