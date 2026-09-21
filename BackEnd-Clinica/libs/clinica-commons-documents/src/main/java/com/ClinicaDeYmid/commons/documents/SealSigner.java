package com.ClinicaDeYmid.commons.documents;

import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;

public interface SealSigner {

    String activeKeyId();

    byte[] sign(String keyId, byte[] data);

    Optional<PublicKey> publicKey(String keyId);

    Map<String, PublicKey> publicKeys();
}
