package com.ClinicaDeYmid.api_gateway.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public final class Browser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).followRedirects(HttpClient.Redirect.NEVER).build();

    public Browser(int port) {
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

        public String header(String name) {
            return raw.headers().firstValue(name).orElse(null);
        }
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Response get(String path, String... headers) {
        return send(request(path, headers).GET());
    }

    public Response send(String method, String path, String body, String... headers) {
        return send(request(path, headers).method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)));
    }

    public Response withCsrf(String method, String path, String body) {
        JsonNode csrf = get("/bff/session").json().get("csrf");
        return send(method, path, body, "Content-Type", "application/json", csrf.get("headerName").asText(), csrf.get("token").asText());
    }

    public static Map<String, String> query(String location) {
        return Arrays.stream(URI.create(location).getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : ""));
    }

    private HttpRequest.Builder request(String path, String... headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(path.startsWith("http") ? path : baseUrl + path));
        for (int index = 0; index + 1 < headers.length; index += 2) {
            request.header(headers[index], headers[index + 1]);
        }
        return request;
    }

    private Response send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(), response);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
