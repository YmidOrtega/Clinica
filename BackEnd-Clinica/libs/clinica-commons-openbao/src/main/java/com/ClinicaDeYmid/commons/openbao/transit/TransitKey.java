package com.ClinicaDeYmid.commons.openbao.transit;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record TransitKey(String name, String type, int latestVersion, int minDecryptionVersion, Map<Integer, String> publicKeysPem) {

    public TransitKey {
        publicKeysPem = Map.copyOf(publicKeysPem);
    }

    public TransitKey requireType(String expected) {
        if (!expected.equals(type)) {
            throw new IllegalStateException("Transit key " + name + " must be of type " + expected + " but is " + type);
        }
        return this;
    }

    public KeyVersion latest() {
        return new KeyVersion(name, latestVersion);
    }

    public boolean usable(KeyVersion version) {
        return version.keyName().equals(name) && version.version() >= minDecryptionVersion && version.version() <= latestVersion;
    }

    public Map<String, Integer> usableVersions() {
        return IntStream.rangeClosed(minDecryptionVersion, latestVersion).boxed()
                .collect(Collectors.toMap(version -> new KeyVersion(name, version).id(), version -> version));
    }
}
