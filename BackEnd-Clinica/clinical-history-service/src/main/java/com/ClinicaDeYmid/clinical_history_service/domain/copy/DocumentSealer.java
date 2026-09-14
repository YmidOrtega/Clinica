package com.ClinicaDeYmid.clinical_history_service.domain.copy;

public interface DocumentSealer {

    DocumentSeal sealDocument(String sha256);

    boolean verifyDocument(String sha256, DocumentSeal seal);

    record DocumentSeal(String keyId, String value) {
    }
}
