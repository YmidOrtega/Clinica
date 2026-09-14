package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;

final class EcPublicKeys {

    private static final Pattern PEM_ARMOR = Pattern.compile("-----(BEGIN|END) [A-Z ]+-----|\\s");

    private EcPublicKeys() {
    }

    static PublicKey parseP256(String pem, String keyId) {
        try {
            PublicKey key = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PEM_ARMOR.matcher(pem).replaceAll(""))));
            if (!(key instanceof ECKey ec) || ec.getParams().getCurve().getField().getFieldSize() != 256) {
                throw new IllegalStateException("Seal key " + keyId + " must use the P-256 curve");
            }
            return key;
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid EC public key for seal key " + keyId, ex);
        }
    }

    static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }
}
