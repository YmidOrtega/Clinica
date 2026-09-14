package com.ClinicaDeYmid.clinical_history_service.domain.attachment;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Attachment(UUID id, String fileName, AttachmentMediaType mediaType, long size, String sha256) {

    public static final long MAX_BYTES = 20L * 1024 * 1024;
    public static final int MAX_PER_NOTE = 10;

    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern UNSAFE_NAME = Pattern.compile("[\\p{Cntrl}/\\\\:*?\"<>|]+");

    public Attachment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(mediaType, "mediaType");
        if (size <= 0 || size > MAX_BYTES || sha256 == null || !SHA_256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("attachment metadata out of range");
        }
    }

    public static Attachment inspect(String originalName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new ClinicalException.InvalidAttachment("el archivo está vacío");
        }
        if (content.length > MAX_BYTES) {
            throw new ClinicalException.InvalidAttachment("supera el tamaño máximo de 20 MB");
        }
        AttachmentMediaType type = AttachmentMediaType.detect(content)
                .orElseThrow(() -> new ClinicalException.InvalidAttachment("solo se aceptan archivos PDF, JPEG o PNG"));
        return new Attachment(UUID.randomUUID(), safeName(originalName, type), type, content.length, sha256Of(content));
    }

    public boolean matches(byte[] content) {
        return content.length == size && sha256.equals(sha256Of(content));
    }

    public static String sha256Of(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String safeName(String originalName, AttachmentMediaType type) {
        String name = originalName == null ? "" : originalName.strip();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = UNSAFE_NAME.matcher(name.substring(slash + 1)).replaceAll("_").strip();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        if (base.isBlank()) {
            base = "anexo";
        }
        if (base.length() > 120) {
            base = base.substring(0, 120);
        }
        return base + "." + type.extension();
    }
}
