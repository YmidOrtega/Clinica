package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;

interface SealSigner {

    String activeKeyId();

    byte[] sign(String keyId, byte[] data);

    Optional<PublicKey> publicKey(String keyId);

    Map<String, PublicKey> publicKeys();
}
