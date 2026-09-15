package com.ClinicaDeYmid.auth_service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class StaffApi {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private final String baseUrl;

    public StaffApi(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public OAuthBrowser.Response get(String path, String token) {
        return send(request(path, token).GET());
    }

    public OAuthBrowser.Response delete(String path, String token) {
        return send(request(path, token).DELETE());
    }

    public OAuthBrowser.Response post(String path, String token, Object body) {
        return post(path, token, null, body);
    }

    public OAuthBrowser.Response post(String path, String token, String ifMatch, Object body) {
        return send(withBody(request(path, token), "POST", ifMatch, body));
    }

    public OAuthBrowser.Response put(String path, String token, String ifMatch, Object body) {
        return send(withBody(request(path, token), "PUT", ifMatch, body));
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    private static HttpRequest.Builder withBody(HttpRequest.Builder request, String method, String ifMatch, Object body) {
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        try {
            return request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "{}" : JSON.writeValueAsString(body)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static OAuthBrowser.Response send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new OAuthBrowser.Response(response.statusCode(), response.body(), response);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
