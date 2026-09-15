package com.ClinicaDeYmid.api_gateway.infrastructure.web;

import com.ClinicaDeYmid.api_gateway.infrastructure.oauth.LoginRedirects;
import com.ClinicaDeYmid.api_gateway.infrastructure.oauth.StaffSessions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/bff")
class BffController {

    private final LoginRedirects redirects;
    private final StaffSessions sessions;

    BffController(LoginRedirects redirects, StaffSessions sessions) {
        this.redirects = redirects;
        this.sessions = sessions;
    }

    record UserView(String uuid, String email, String name, String role, Instant authenticatedAt, List<String> methods) {

        static UserView from(OidcUser user) {
            return new UserView(user.getSubject(), user.getEmail(), user.getFullName(), user.getClaimAsString("role"), user.getAuthenticatedAt(),
                    Optional.ofNullable(user.getClaimAsStringList("amr")).orElse(List.of()));
        }
    }

    record CsrfView(String headerName, String token) {
    }

    record SessionView(boolean authenticated, UserView user, CsrfView csrf, String loginUrl, String stepUpUrl, String logoutUrl) {
    }

    record LogoutView(String endSessionUrl) {
    }

    @GetMapping("/session")
    SessionView session(@AuthenticationPrincipal OidcUser user, HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return new SessionView(user != null, user == null ? null : UserView.from(user), new CsrfView(csrf.getHeaderName(), csrf.getToken()),
                "/bff/login", "/bff/step-up", "/bff/logout");
    }

    @GetMapping("/login")
    ResponseEntity<Void> login(@RequestParam(required = false) String returnTo, HttpServletRequest request) {
        return redirect(redirects.begin(request, returnTo, false));
    }

    @GetMapping("/step-up")
    ResponseEntity<Void> stepUp(@RequestParam(required = false) String returnTo, HttpServletRequest request) {
        return redirect(redirects.begin(request, returnTo, true));
    }

    @PostMapping("/logout")
    LogoutView logout(HttpServletRequest request) {
        return new LogoutView(sessions.end(request).orElse(null));
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create(location).toString()).build();
    }
}
