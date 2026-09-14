package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class SealKeyRing {

    static final String ALGORITHM = "SHA256withECDSA";

    private static final String PUBLIC_SUFFIX = ".public.pem";
    private static final String PRIVATE_SUFFIX = ".private.pem";
    private static final Pattern KEY_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final Pattern PEM_ARMOR = Pattern.compile("-----(BEGIN|END) [A-Z ]+-----|\\s");

    private final String activeKeyId;
    private final PrivateKey activeKey;
    private final Map<String, PublicKey> publicKeys;

    private SealKeyRing(String activeKeyId, PrivateKey activeKey, Map<String, PublicKey> publicKeys) {
        this.activeKeyId = activeKeyId;
        this.activeKey = activeKey;
        this.publicKeys = Collections.unmodifiableMap(publicKeys);
    }

    static SealKeyRing load(SealProperties properties) {
        if (properties.keysLocation() == null || properties.activeKeyId() == null || properties.activeKeyId().isBlank()) {
            throw new IllegalStateException("clinica.clinical.seal.keys-location and active-key-id are required to seal the clinical record");
        }
        String activeKeyId = properties.activeKeyId().strip();
        if (!KEY_ID.matcher(activeKeyId).matches()) {
            throw new IllegalStateException("The active seal key id has invalid characters");
        }
        Map<String, PublicKey> publicKeys = new TreeMap<>();
        try (Stream<Path> files = Files.list(properties.keysLocation())) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(PUBLIC_SUFFIX)).toList()) {
                String keyId = file.getFileName().toString().substring(0, file.getFileName().toString().length() - PUBLIC_SUFFIX.length());
                if (KEY_ID.matcher(keyId).matches()) {
                    publicKeys.put(keyId, readPublic(file));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the seal keys directory", ex);
        }
        Path privateFile = properties.keysLocation().resolve(activeKeyId + PRIVATE_SUFFIX);
        if (!Files.isReadable(privateFile) || !publicKeys.containsKey(activeKeyId)) {
            throw new IllegalStateException("The active seal key " + activeKeyId + " needs both its private and public PEM files");
        }
        PrivateKey activeKey = readPrivate(privateFile);
        requireMatchingPair(activeKey, publicKeys.get(activeKeyId));
        return new SealKeyRing(activeKeyId, activeKey, publicKeys);
    }

    String activeKeyId() {
        return activeKeyId;
    }

    byte[] sign(byte[] data) {
        try {
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(activeKey);
            signer.update(data);
            return signer.sign();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not seal a clinical record entry", ex);
        }
    }

    boolean knows(String keyId) {
        return publicKeys.containsKey(keyId);
    }

    Optional<Boolean> verify(String keyId, byte[] data, byte[] seal) {
        PublicKey key = publicKeys.get(keyId);
        if (key == null) {
            return Optional.empty();
        }
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(key);
            verifier.update(data);
            return Optional.of(verifier.verify(seal));
        } catch (GeneralSecurityException malformedSeal) {
            return Optional.of(false);
        }
    }

    Map<String, String> publicKeysPem() {
        Map<String, String> pem = new TreeMap<>();
        publicKeys.forEach((keyId, key) -> pem.put(keyId, "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----\n"));
        return pem;
    }

    private static PublicKey readPublic(Path file) {
        try {
            return requireP256(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der(file))), file);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid EC public key in " + file.getFileName(), ex);
        }
    }

    private static PrivateKey readPrivate(Path file) {
        try {
            return requireP256(KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der(file))), file);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid EC private key in " + file.getFileName() + "; it must be PKCS#8 PEM", ex);
        }
    }

    private static byte[] der(Path file) {
        try {
            return Base64.getDecoder().decode(PEM_ARMOR.matcher(Files.readString(file, StandardCharsets.US_ASCII)).replaceAll(""));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read " + file.getFileName(), ex);
        }
    }

    private static <K extends Key> K requireP256(K key, Path file) {
        if (!(key instanceof ECKey ec) || ec.getParams().getCurve().getField().getFieldSize() != 256) {
            throw new IllegalStateException("Seal key " + file.getFileName() + " must use the P-256 curve");
        }
        return key;
    }

    private static void requireMatchingPair(PrivateKey privateKey, PublicKey publicKey) {
        byte[] probe = "clinical-seal-key-check".getBytes(StandardCharsets.US_ASCII);
        try {
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(privateKey);
            signer.update(probe);
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(probe);
            if (!verifier.verify(signer.sign())) {
                throw new IllegalStateException("The active seal private and public keys do not match");
            }
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not check the active seal key pair", ex);
        }
    }
}
