package com.ClinicaDeYmid.commons.openbao.testing;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

public final class TotpCodes {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int PERIOD_SECONDS = 30;

    private TotpCodes() {
    }

    public static String at(String otpauthUrl, Instant instant) {
        byte[] key = base32(secretOf(otpauthUrl));
        long counter = instant.getEpochSecond() / PERIOD_SECONDS;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ByteBuffer.wrap(hash, offset, Integer.BYTES).getInt() & 0x7fffffff;
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static Instant nextPeriodStart(Instant instant) {
        return Instant.ofEpochSecond((instant.getEpochSecond() / PERIOD_SECONDS + 1) * PERIOD_SECONDS);
    }

    private static String secretOf(String otpauthUrl) {
        return Arrays.stream(URI.create(otpauthUrl).getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(pair -> pair[0].equals("secret"))
                .map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("The otpauth URL has no secret"));
    }

    private static byte[] base32(String encoded) {
        ByteBuffer bytes = ByteBuffer.allocate(encoded.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char character : encoded.toUpperCase(Locale.ROOT).replace("=", "").toCharArray()) {
            buffer = (buffer << 5) | BASE32.indexOf(character);
            bits += 5;
            if (bits >= 8) {
                bytes.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return Arrays.copyOf(bytes.array(), bytes.position());
    }
}
