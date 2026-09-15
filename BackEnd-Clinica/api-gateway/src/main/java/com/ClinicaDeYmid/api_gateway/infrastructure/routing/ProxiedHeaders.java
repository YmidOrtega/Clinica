package com.ClinicaDeYmid.api_gateway.infrastructure.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
class ProxiedHeaders {

    private final String sessionCookie;

    ProxiedHeaders(@Value("${server.servlet.session.cookie.name}") String sessionCookie) {
        this.sessionCookie = sessionCookie;
    }

    ServerRequest forStaffApi(ServerRequest request, String accessToken) {
        return ServerRequest.from(request).headers(headers -> {
            withoutClientForwarding(headers);
            headers.remove(HttpHeaders.COOKIE);
            headers.remove("X-XSRF-TOKEN");
            headers.remove("X-CSRF-TOKEN");
            headers.setBearerAuth(accessToken);
        }).build();
    }

    ServerRequest forAuthService(ServerRequest request) {
        return ServerRequest.from(request).headers(headers -> {
            withoutClientForwarding(headers);
            headers.remove(HttpHeaders.AUTHORIZATION);
            String cookies = headers.getOrEmpty(HttpHeaders.COOKIE).stream()
                    .flatMap(header -> Arrays.stream(header.split(";")))
                    .map(String::strip)
                    .filter(cookie -> !cookie.isEmpty() && !cookie.startsWith(sessionCookie + "="))
                    .collect(Collectors.joining("; "));
            headers.remove(HttpHeaders.COOKIE);
            if (!cookies.isEmpty()) {
                headers.set(HttpHeaders.COOKIE, cookies);
            }
        }).build();
    }

    private static void withoutClientForwarding(HttpHeaders headers) {
        headers.remove("Forwarded");
        headers.keySet().removeIf(name -> name.regionMatches(true, 0, "X-Forwarded-", 0, "X-Forwarded-".length()));
    }

    ServerResponse withoutCorsHeaders(ServerRequest request, ServerResponse response) {
        response.headers().keySet().removeIf(name -> name.regionMatches(true, 0, "Access-Control-", 0, "Access-Control-".length()));
        return response;
    }
}
