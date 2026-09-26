package com.ClinicaDeYmid.billing_service.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public final class TestCertificates {

    private static final byte[] SHA256_WITH_RSA = sequence(oid(1, 2, 840, 113549, 1, 1, 11), new byte[]{0x05, 0x00});
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private TestCertificates() {
    }

    public static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static X509Certificate selfSigned(KeyPair keys, String commonName, Instant notBefore, Instant notAfter) {
        byte[] name = sequence(set(sequence(oid(2, 5, 4, 3), tagged(0x0c, commonName.getBytes(StandardCharsets.UTF_8)))));
        byte[] tbs = sequence(
                tagged(0xa0, integer(BigInteger.TWO)),
                integer(BigInteger.valueOf(System.nanoTime()).abs()),
                SHA256_WITH_RSA,
                name,
                sequence(time(notBefore), time(notAfter)),
                name,
                keys.getPublic().getEncoded());
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keys.getPrivate());
            signature.update(tbs);
            byte[] signed = signature.sign();
            byte[] bitString = new byte[signed.length + 1];
            System.arraycopy(signed, 0, bitString, 1, signed.length);
            byte[] der = sequence(tbs, SHA256_WITH_RSA, tagged(0x03, bitString));
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(der));
        } catch (GeneralSecurityException broken) {
            throw new IllegalStateException(broken);
        }
    }

    public static String pem(X509Certificate certificate) {
        try {
            return "-----BEGIN CERTIFICATE-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(certificate.getEncoded())
                    + "\n-----END CERTIFICATE-----\n";
        } catch (GeneralSecurityException broken) {
            throw new IllegalStateException(broken);
        }
    }

    private static byte[] time(Instant instant) {
        return tagged(0x17, UTC_TIME.format(instant).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] integer(BigInteger value) {
        return tagged(0x02, value.toByteArray());
    }

    private static byte[] oid(int... arcs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(arcs[0] * 40 + arcs[1]);
        for (int i = 2; i < arcs.length; i++) {
            int arc = arcs[i];
            byte[] base128 = new byte[5];
            int length = 0;
            do {
                base128[length++] = (byte) (arc & 0x7f);
                arc >>>= 7;
            } while (arc != 0);
            for (int j = length - 1; j >= 0; j--) {
                out.write(base128[j] | (j == 0 ? 0 : 0x80));
            }
        }
        return tagged(0x06, out.toByteArray());
    }

    private static byte[] sequence(byte[]... parts) {
        return tagged(0x30, concat(parts));
    }

    private static byte[] set(byte[]... parts) {
        return tagged(0x31, concat(parts));
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static byte[] tagged(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int length = content.length;
        if (length < 0x80) {
            out.write(length);
        } else {
            byte[] bytes = BigInteger.valueOf(length).toByteArray();
            int offset = bytes[0] == 0 ? 1 : 0;
            out.write(0x80 | (bytes.length - offset));
            out.write(bytes, offset, bytes.length - offset);
        }
        out.writeBytes(content);
        return out.toByteArray();
    }
}
