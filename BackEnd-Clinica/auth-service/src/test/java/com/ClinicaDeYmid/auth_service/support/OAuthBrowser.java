package com.ClinicaDeYmid.auth_service.support;

import com.ClinicaDeYmid.commons.openbao.testing.OpenBaoTestContainer;
import com.ClinicaDeYmid.commons.openbao.transit.KeyVersion;
import com.ClinicaDeYmid.commons.openbao.transit.SignatureFormat;
import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class OAuthBrowser {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String codeVerifier = BASE64URL.encodeToString(random(32));
    private final String state = BASE64URL.encodeToString(random(16));

    public OAuthBrowser(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public record Response(int status, String body, HttpResponse<String> raw) {

        public JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (IOException ex) {
                throw new IllegalStateException(body, ex);
            }
        }

        public String location() {
            return raw.headers().firstValue("Location").orElse(null);
        }
    }

    public Response authorize() {
        return get(authorizationUrl());
    }

    public String authorizationUrl() {
        return baseUrl + "/oauth2/authorize?" + form(Map.of(
                "response_type", "code",
                "client_id", AuthTestSupport.CLIENT_ID,
                "redirect_uri", AuthTestSupport.REDIRECT_URI,
                "scope", "openid profile",
                "state", state,
                "code_challenge", challenge(),
                "code_challenge_method", "S256"));
    }

    public String signIn(StaffAccounts.StaffAccount account) {
        Response login = login(account.email(), StaffAccounts.PASSWORD);
        if (login.status() != 200 || !"SECOND_FACTOR_REQUIRED".equals(login.json().get("outcome").asText())) {
            throw new IllegalStateException("Expected the second factor step but got " + login.status() + " " + login.body());
        }
        Response verified = postJson("/api/v1/login/second-factor", Map.of("code", account.totpCode()), true);
        if (verified.status() != 200) {
            throw new IllegalStateException("The second factor was rejected: " + verified.body());
        }
        return verified.json().get("continueUrl").asText();
    }

    public Response login(String email, String password) {
        return postJson("/api/v1/login", Map.of("email", email, "password", password), true);
    }

    public Response postJson(String path, Map<String, String> body, boolean withCsrf) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(write(body)));
        if (withCsrf) {
            JsonNode csrf = get(baseUrl + "/api/v1/session").json().get("csrf");
            request.header(csrf.get("headerName").asText(), csrf.get("token").asText());
        }
        return send(request.build());
    }

    public Response get(String url) {
        return send(HttpRequest.newBuilder(URI.create(url.startsWith("http") ? url : baseUrl + url)).GET().build());
    }

    public String authorizationCode(String continueUrl) {
        Response redirect = get(continueUrl);
        if (redirect.status() != 302 || !redirect.location().startsWith(AuthTestSupport.REDIRECT_URI)) {
            throw new IllegalStateException("Expected a redirect to the client but got " + redirect.status() + " " + redirect.location());
        }
        Map<String, String> parameters = query(URI.create(redirect.location()));
        if (!state.equals(parameters.get("state"))) {
            throw new IllegalStateException("The state did not round trip");
        }
        return parameters.get("code");
    }

    public Response exchangeCode(String code) {
        return token(Map.of("grant_type", "authorization_code", "code", code, "redirect_uri", AuthTestSupport.REDIRECT_URI,
                "code_verifier", codeVerifier));
    }

    public Response refresh(String refreshToken) {
        return token(Map.of("grant_type", "refresh_token", "refresh_token", refreshToken));
    }

    public Response token(Map<String, String> grant) {
        Map<String, String> parameters = new LinkedHashMap<>(grant);
        parameters.put("client_id", AuthTestSupport.CLIENT_ID);
        parameters.putIfAbsent("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        parameters.putIfAbsent("client_assertion", clientAssertion());
        return send(HttpRequest.newBuilder(URI.create(baseUrl + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(parameters)))
                .build());
    }

    public static String clientAssertion() {
        TransitClient transit = new TransitClient(OpenBaoTestContainer.template(), "transit");
        KeyVersion version = transit.key(AuthTestSupport.CLIENT_ASSERTION_KEY).latest();
        long now = Instant.now().getEpochSecond();
        String header = BASE64URL.encodeToString(write(Map.of("alg", "ES256", "typ", "JWT")).getBytes(StandardCharsets.UTF_8));
        String payload = BASE64URL.encodeToString(write(Map.of(
                "iss", AuthTestSupport.CLIENT_ID,
                "sub", AuthTestSupport.CLIENT_ID,
                "aud", AuthTestSupport.ISSUER,
                "iat", now,
                "exp", now + 60,
                "jti", UUID.randomUUID().toString())).getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        byte[] signature = transit.sign(version, signingInput.getBytes(StandardCharsets.US_ASCII), SignatureFormat.JWS);
        return signingInput + "." + BASE64URL.encodeToString(signature);
    }

    private Response send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(), response);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private String challenge() {
        try {
            return BASE64URL.encodeToString(MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&")).map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

    private static String form(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] random(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return value;
    }
}
