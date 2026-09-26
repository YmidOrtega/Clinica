package com.ClinicaDeYmid.billing_service.application.dian;

import com.ClinicaDeYmid.billing_service.domain.Nit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public record DianPackage(String xmlName, String zipName, byte[] zip) {

    static final String OWN_SOFTWARE = "000";

    public static String baseName(Nit issuer, int year, long consecutive) {
        if (consecutive < 1 || consecutive > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("The DIAN file consecutive must fit in 8 hexadecimal digits");
        }
        return "%010d".formatted(Long.parseLong(issuer.number())) + OWN_SOFTWARE
                + "%02d".formatted(year % 100) + "%08x".formatted(consecutive);
    }

    public static DianPackage of(String baseName, String signedUbl) {
        String xmlName = "fv" + baseName + ".xml";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(xmlName));
            zip.write(signedUbl.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return new DianPackage(xmlName, "z" + baseName + ".zip", out.toByteArray());
    }
}
