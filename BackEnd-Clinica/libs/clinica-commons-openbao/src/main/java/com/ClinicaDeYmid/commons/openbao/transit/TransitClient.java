package com.ClinicaDeYmid.commons.openbao.transit;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransitClient {

    private static final Pattern STATUS = Pattern.compile("^Status (\\d{3})\\b");
    private static final Pattern CIPHERTEXT_OR_SIGNATURE = Pattern.compile("^vault:v([1-9][0-9]*):(.+)$");

    private final VaultOperations vault;
    private final String mount;

    public TransitClient(VaultOperations vault, String mount) {
        this.vault = vault;
        this.mount = mount;
    }

    public byte[] encrypt(KeyVersion key, byte[] plaintext, byte[] associatedData) {
        Map<String, Object> data = write("encrypt/" + key.keyName(), Map.of(
                "plaintext", base64(plaintext),
                "associated_data", base64(associatedData),
                "key_version", key.version()));
        String ciphertext = (String) data.get("ciphertext");
        requireVersion(ciphertext, key);
        return ciphertext.getBytes(StandardCharsets.US_ASCII);
    }

    public byte[] decrypt(KeyVersion key, byte[] ciphertext, byte[] associatedData) {
        String value = new String(ciphertext, StandardCharsets.US_ASCII);
        requireVersion(value, key);
        Map<String, Object> data = write("decrypt/" + key.keyName(), Map.of(
                "ciphertext", value,
                "associated_data", base64(associatedData)));
        return Base64.getDecoder().decode((String) data.get("plaintext"));
    }

    public byte[] sign(KeyVersion key, byte[] input, SignatureFormat format) {
        Map<String, Object> data = write("sign/" + key.keyName(), Map.of(
                "input", base64(input),
                "hash_algorithm", "sha2-256",
                "marshaling_algorithm", format.marshalingAlgorithm(),
                "key_version", key.version()));
        String signature = (String) data.get("signature");
        return format.decode(requireVersion(signature, key));
    }

    public TransitKey key(String name) {
        Map<String, Object> data = call(HttpMethod.GET, "keys/" + name, null);
        Map<Integer, String> publicKeys = new HashMap<>();
        if (data.get("keys") instanceof Map<?, ?> versions) {
            versions.forEach((version, details) -> {
                if (details instanceof Map<?, ?> fields && fields.get("public_key") instanceof String pem && !pem.isBlank()) {
                    publicKeys.put(Integer.parseInt(version.toString()), pem);
                }
            });
        }
        return new TransitKey(name, (String) data.get("type"), ((Number) data.get("latest_version")).intValue(),
                ((Number) data.get("min_decryption_version")).intValue(), publicKeys);
    }

    private Map<String, Object> write(String operation, Map<String, Object> body) {
        return call(HttpMethod.POST, operation, body);
    }

    private Map<String, Object> call(HttpMethod method, String operation, Map<String, Object> body) {
        String path = mount + "/" + operation;
        try {
            VaultResponse response = vault.doWithSession(rest -> rest.exchange(path, method,
                    body == null ? HttpEntity.EMPTY : new HttpEntity<>(body), VaultResponse.class).getBody());
            if (response == null || response.getData() == null) {
                throw new OpenBaoUnavailableException("OpenBao returned no data for " + path, null);
            }
            return response.getData();
        } catch (VaultException failed) {
            if (statusOf(failed) == HttpStatus.BAD_REQUEST.value()) {
                throw new TransitRejectedException("OpenBao rejected " + path + ": " + failed.getMessage(), failed);
            }
            throw new OpenBaoUnavailableException("OpenBao failed for " + path + ": " + failed.getMessage(), failed);
        } catch (RestClientException unreachable) {
            throw new OpenBaoUnavailableException("OpenBao is not reachable for " + path, unreachable);
        }
    }

    private static int statusOf(VaultException failed) {
        for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpStatusCodeException http) {
                return http.getStatusCode().value();
            }
        }
        Matcher status = STATUS.matcher(String.valueOf(failed.getMessage()));
        return status.lookingAt() ? Integer.parseInt(status.group(1)) : -1;
    }

    private static String requireVersion(String value, KeyVersion key) {
        Matcher matcher = value == null ? null : CIPHERTEXT_OR_SIGNATURE.matcher(value);
        if (matcher == null || !matcher.matches() || Integer.parseInt(matcher.group(1)) != key.version()) {
            throw new TransitRejectedException("Transit value does not belong to " + key.id(), null);
        }
        return matcher.group(2);
    }

    private static String base64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }
}
