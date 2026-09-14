package com.ClinicaDeYmid.clinical_history_service.domain.attachment;

import java.util.Arrays;
import java.util.Optional;

public enum AttachmentMediaType {
    PDF("application/pdf", "pdf", new byte[]{'%', 'P', 'D', 'F', '-'}),
    JPEG("image/jpeg", "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", "png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

    private final String mimeType;
    private final String extension;
    private final byte[] signature;

    AttachmentMediaType(String mimeType, String extension, byte[] signature) {
        this.mimeType = mimeType;
        this.extension = extension;
        this.signature = signature;
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<AttachmentMediaType> detect(byte[] content) {
        return Arrays.stream(values())
                .filter(type -> content.length >= type.signature.length
                        && Arrays.equals(content, 0, type.signature.length, type.signature, 0, type.signature.length))
                .findFirst();
    }
}
