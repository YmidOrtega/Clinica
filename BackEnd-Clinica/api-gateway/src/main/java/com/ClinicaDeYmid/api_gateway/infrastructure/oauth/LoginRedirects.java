package com.ClinicaDeYmid.api_gateway.infrastructure.oauth;

import com.ClinicaDeYmid.api_gateway.infrastructure.config.GatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

@Component
public class LoginRedirects implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginRedirects.class);

    static final String RETURN_TO = LoginRedirects.class.getName() + ".RETURN_TO";
    static final String STEP_UP = LoginRedirects.class.getName() + ".STEP_UP";
    public static final String AUTHORIZATION_PATH = "/oauth2/authorization/" + AuthServerClientConfiguration.REGISTRATION_ID;

    private final List<String> origins;
    private final URI home;

    LoginRedirects(GatewayProperties properties) {
        this.origins = properties.frontend().origins();
        this.home = properties.frontend().homeUrl();
    }

    public String begin(HttpServletRequest request, String returnTo, boolean stepUp) {
        HttpSession session = request.getSession(true);
        session.setAttribute(RETURN_TO, accepted(returnTo).toString());
        if (stepUp) {
            session.setAttribute(STEP_UP, Boolean.TRUE);
        } else {
            session.removeAttribute(STEP_UP);
        }
        return AUTHORIZATION_PATH;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        HttpSession session = request.getSession(false);
        String target = Optional.ofNullable(session).map(current -> (String) current.getAttribute(RETURN_TO)).orElse(home.toString());
        if (session != null) {
            session.removeAttribute(RETURN_TO);
            session.removeAttribute(STEP_UP);
        }
        response.sendRedirect(target);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        log.warn("Sign-in through auth-service failed: {}", exception.getMessage());
        response.sendRedirect(UriComponentsBuilder.fromUri(home).queryParam("error", "login").build().toUriString());
    }

    URI accepted(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return home;
        }
        try {
            URI candidate = URI.create(returnTo);
            if (candidate.getScheme() == null || candidate.getHost() == null || candidate.getUserInfo() != null) {
                return home;
            }
            String origin = candidate.getScheme() + "://" + candidate.getHost() + (candidate.getPort() == -1 ? "" : ":" + candidate.getPort());
            return origins.contains(origin) ? candidate : home;
        } catch (IllegalArgumentException malformed) {
            return home;
        }
    }
}
