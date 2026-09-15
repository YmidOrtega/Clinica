package com.ClinicaDeYmid.commons.openbao;

import com.ClinicaDeYmid.commons.openbao.transit.OpenBaoUnavailableException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpenBaoHttp {

    private static final Pattern STATUS = Pattern.compile("^Status (\\d{3})\\b");

    private OpenBaoHttp() {
    }

    public static Map<String, Object> exchange(VaultOperations vault, HttpMethod method, String path, Map<String, Object> body,
                                               BiFunction<String, VaultException, RuntimeException> onBadRequest) {
        try {
            VaultResponse response = vault.doWithSession(rest -> rest.exchange(path, method,
                    body == null ? HttpEntity.EMPTY : new HttpEntity<>(body), VaultResponse.class).getBody());
            if (response == null || response.getData() == null) {
                if (method == HttpMethod.DELETE) {
                    return Map.of();
                }
                throw new OpenBaoUnavailableException("OpenBao returned no data for " + path, null);
            }
            return response.getData();
        } catch (VaultException failed) {
            if (statusOf(failed) == HttpStatus.BAD_REQUEST.value()) {
                throw onBadRequest.apply("OpenBao rejected " + path + ": " + failed.getMessage(), failed);
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
}
