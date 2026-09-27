package com.ClinicaDeYmid.billing_service.infrastructure.ministry;

import com.ClinicaDeYmid.billing_service.application.rips.MinistryAnswer;
import com.ClinicaDeYmid.billing_service.application.rips.MinistryValidator;
import com.ClinicaDeYmid.billing_service.application.rips.RipsDocument;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.MinistryFinding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RestMinistryValidator implements MinistryValidator {

    static final String LOGIN = "/api/Auth/LoginSISPRO";
    static final String SUBMIT = "/api/PaquetesFevRips/CargarFevRips";
    static final String RECOVER = "/api/ConsultasFevRips/RecuperarCUV";
    static final String EXPIRED_TOKEN = "TOT002";
    static final Duration TOKEN_LIFETIME = Duration.ofMinutes(110);
    static final Duration TOKEN_MARGIN = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(RestMinistryValidator.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient http;
    private final MinistryCredentials credentials;
    private final Clock clock;
    private String token;
    private Instant tokenExpiresAt = Instant.MIN;

    public RestMinistryValidator(RestClient http, MinistryCredentials credentials, Clock clock) {
        this.http = http;
        this.credentials = credentials;
        this.clock = clock;
    }

    @Override
    public String wireFormat(RipsDocument rips) {
        try {
            return JSON.writeValueAsString(rips);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Cannot write the RIPS JSON", impossible);
        }
    }

    @Override
    public MinistryAnswer submit(String ripsJson, String attachedDocument, String providerNit) {
        ObjectNode body = JSON.createObjectNode();
        body.set("rips", parse(ripsJson));
        body.put("xmlFevFile", attachedDocument);
        return authenticated(SUBMIT, body, providerNit);
    }

    @Override
    public MinistryAnswer recover(String cuv, String providerNit) {
        ObjectNode body = JSON.createObjectNode();
        body.put("codigoUnicoValidacion", cuv);
        return authenticated(RECOVER, body, providerNit);
    }

    private MinistryAnswer authenticated(String path, JsonNode body, String providerNit) {
        Exchange first = post(path, body, token(providerNit, false));
        if (first.status() == 401 || first.expiredToken()) {
            return answer(post(path, body, token(providerNit, true)), path);
        }
        return answer(first, path);
    }

    private synchronized String token(String providerNit, boolean renew) {
        if (!renew && token != null && clock.instant().isBefore(tokenExpiresAt)) {
            return token;
        }
        if (!credentials.configured()) {
            throw new BillingException.MinistryCredentialsMissing();
        }
        ObjectNode body = JSON.createObjectNode();
        ObjectNode identification = body.putObject("persona").putObject("identificacion");
        identification.put("tipo", credentials.documentType());
        identification.put("numero", credentials.documentNumber());
        body.put("clave", credentials.password());
        body.put("nit", providerNit);
        Exchange login = post(LOGIN, body, null);
        JsonNode granted = login.json() == null ? null : field(login.json(), "login");
        if ((granted != null && !granted.asBoolean(false)) || login.status() == 401 || login.status() == 403) {
            log.warn("SISPRO rejected the login of the ministry validator ({}): {}", login.status(),
                    login.json() == null ? null : field(login.json(), "errors"));
            throw new BillingException.MinistryCredentialsRejected();
        }
        String issued = login.json() == null ? null : text(login.json(), "token");
        if (login.status() / 100 != 2 || issued == null || issued.isBlank()) {
            log.warn("The ministry validator did not issue a SISPRO token ({})", login.status());
            throw new BillingException.MinistryUnavailable();
        }
        token = issued;
        tokenExpiresAt = expiryOf(issued);
        return token;
    }

    private Instant expiryOf(String jwt) {
        Instant fallback = clock.instant().plus(TOKEN_LIFETIME);
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            return fallback;
        }
        try {
            JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode expiry = claims == null ? null : claims.get("exp");
            if (expiry == null || !expiry.canConvertToLong()) {
                return fallback;
            }
            return Instant.ofEpochSecond(expiry.asLong()).minus(TOKEN_MARGIN);
        } catch (IllegalArgumentException | IOException unreadable) {
            return fallback;
        }
    }

    private Exchange post(String path, JsonNode body, String bearer) {
        try {
            return http.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (bearer != null) {
                            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
                        }
                    })
                    .body(JSON.writeValueAsBytes(body))
                    .exchange((sent, received) -> {
                        String raw = new String(received.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        return new Exchange(received.getStatusCode().value(), raw, parseQuietly(raw));
                    });
        } catch (RestClientException | JsonProcessingException unreachable) {
            log.warn("The ministry validator could not be reached at {}: {}", path, unreachable.getMessage());
            throw new BillingException.MinistryUnavailable();
        }
    }

    private MinistryAnswer answer(Exchange exchange, String path) {
        JsonNode json = exchange.json();
        if (json == null || field(json, "ResultState") == null) {
            log.warn("The ministry validator answered {} to {} without a validation result", exchange.status(), path);
            throw new BillingException.MinistryUnavailable();
        }
        List<MinistryFinding> findings = new ArrayList<>();
        JsonNode results = field(json, "ResultadosValidacion");
        if (results != null && results.isArray()) {
            for (JsonNode result : results) {
                findings.add(new MinistryFinding(text(result, "Clase"), text(result, "Codigo"),
                        text(result, "Descripcion"), text(result, "Observaciones"), text(result, "PathFuente"),
                        text(result, "Fuente")));
            }
        }
        JsonNode process = field(json, "ProcesoId");
        return new MinistryAnswer(field(json, "ResultState").asBoolean(false),
                process == null || !process.canConvertToLong() ? null : process.asLong(),
                text(json, "NumFactura"), text(json, "CodigoUnicoValidacion"), instant(text(json, "FechaRadicacion")),
                findings, exchange.raw());
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException unknown) {
            return null;
        }
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException broken) {
            throw new IllegalStateException("The stored RIPS is not JSON", broken);
        }
    }

    private static JsonNode parseQuietly(String raw) {
        try {
            JsonNode node = JSON.readTree(raw);
            return node != null && node.isObject() ? node : null;
        } catch (JsonProcessingException notJson) {
            return null;
        }
    }

    static JsonNode field(JsonNode node, String name) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue().isNull() ? null : entry.getValue();
            }
        }
        return null;
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = field(node, name);
        return value == null ? null : value.asText();
    }

    private record Exchange(int status, String raw, JsonNode json) {

        boolean expiredToken() {
            JsonNode results = json == null ? null : field(json, "ResultadosValidacion");
            if (results == null || !results.isArray()) {
                return false;
            }
            for (JsonNode result : results) {
                JsonNode code = field(result, "Codigo");
                if (code != null && EXPIRED_TOKEN.equals(code.asText().toUpperCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }
}
