package com.ClinicaDeYmid.commons.documents;

import java.util.Map;

public interface DocumentSealer {

    DocumentSeal seal(String sha256);

    boolean verify(String sha256, DocumentSeal seal);

    String activeKeyId();

    Map<String, String> publicKeysPem();
}
