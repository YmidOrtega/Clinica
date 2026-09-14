package com.ClinicaDeYmid.clinical_history_service.support;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class SampleFiles {

    private SampleFiles() {
    }

    public static byte[] pdf(String text) {
        return ("%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\n% " + text + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] png() {
        byte[] content = Arrays.copyOf(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 64);
        Arrays.fill(content, 8, 64, (byte) 7);
        return content;
    }
}
