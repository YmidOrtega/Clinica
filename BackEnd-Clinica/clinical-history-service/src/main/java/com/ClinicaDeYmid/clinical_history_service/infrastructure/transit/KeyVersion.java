package com.ClinicaDeYmid.clinical_history_service.infrastructure.transit;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record KeyVersion(String keyName, int version) {

    private static final Pattern ID = Pattern.compile("^([a-z0-9][a-z0-9-]*)-v([1-9][0-9]{0,8})$");

    public KeyVersion {
        if (!ID.matcher(keyName + "-v" + version).matches()) {
            throw new IllegalArgumentException("Invalid transit key version " + keyName + " v" + version);
        }
    }

    public static Optional<KeyVersion> parse(String keyId) {
        Matcher matcher = ID.matcher(keyId);
        return matcher.matches() ? Optional.of(new KeyVersion(matcher.group(1), Integer.parseInt(matcher.group(2)))) : Optional.empty();
    }

    public String id() {
        return keyName + "-v" + version;
    }
}
