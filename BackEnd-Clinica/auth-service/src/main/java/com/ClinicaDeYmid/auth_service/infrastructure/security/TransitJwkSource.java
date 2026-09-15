package com.ClinicaDeYmid.auth_service.infrastructure.security;

import com.ClinicaDeYmid.commons.openbao.transit.KeyVersion;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKey;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

final class TransitJwkSource implements JWKSource<SecurityContext> {

    static final String KEY_TYPE = "ecdsa-p256";

    private static final Pattern PEM_ARMOR = Pattern.compile("-----(BEGIN|END) PUBLIC KEY-----");

    private final TransitKeys keys;
    private final int publishedVersions;

    TransitJwkSource(TransitKeys keys, int publishedVersions) {
        this.keys = keys;
        this.publishedVersions = publishedVersions;
        keys.current().requireType(KEY_TYPE);
    }

    static TransitJwkSource allVersions(TransitKeys keys) {
        return new TransitJwkSource(keys, Integer.MAX_VALUE);
    }

    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) {
        TransitKey key = keys.current();
        for (String keyId : selector.getMatcher().getKeyIDs() == null ? List.<String>of() : selector.getMatcher().getKeyIDs()) {
            key = KeyVersion.parse(keyId).map(keys::refreshIfUnknown).orElse(key);
        }
        return selector.select(new JWKSet(jwks(key)));
    }

    List<JWK> jwks(TransitKey key) {
        int oldest = (int) Math.max(key.minDecryptionVersion(), (long) key.latestVersion() - publishedVersions + 1);
        List<JWK> jwks = new ArrayList<>();
        for (int version = key.latestVersion(); version >= oldest; version--) {
            String pem = key.publicKeysPem().get(version);
            if (pem != null) {
                jwks.add(jwk(new KeyVersion(key.name(), version), pem));
            }
        }
        return jwks;
    }

    private static JWK jwk(KeyVersion version, String pem) {
        try {
            byte[] der = Base64.getMimeDecoder().decode(PEM_ARMOR.matcher(pem).replaceAll(""));
            ECPublicKey publicKey = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
            return new ECKey.Builder(Curve.P_256, publicKey).keyID(version.id()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.ES256).build();
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException ex) {
            throw new IllegalStateException("Transit returned an invalid public key for " + version.id(), ex);
        }
    }
}
