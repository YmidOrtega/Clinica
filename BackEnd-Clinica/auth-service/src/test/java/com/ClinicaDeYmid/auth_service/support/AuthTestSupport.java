package com.ClinicaDeYmid.auth_service.support;

import com.ClinicaDeYmid.auth_service.application.mail.Mailer;
import com.ClinicaDeYmid.commons.openbao.testing.OpenBaoTestContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@TestConfiguration(proxyBeanMethods = false)
public class AuthTestSupport {

    public static final String ISSUER = "http://auth.clinica.test";
    public static final String LOGIN_URL = "http://clinica.test/login";
    public static final String HOME_URL = "http://clinica.test/";
    public static final String CLIENT_ID = "api-gateway";
    public static final String REDIRECT_URI = "http://gateway.clinica.test/login/oauth2/code/clinica";
    public static final String SIGNING_KEY = OpenBaoTestContainer.ensureKey("auth-jwt", "ecdsa-p256");
    public static final String CLIENT_ASSERTION_KEY = OpenBaoTestContainer.ensureKey("api-gateway-client", "ecdsa-p256");

    @Bean
    BearerTokens bearerTokens(JwtEncoder encoder) {
        return new BearerTokens(encoder);
    }

    @Bean
    @Primary
    CapturingMailer capturingMailer() {
        return new CapturingMailer();
    }

    public static void register(DynamicPropertyRegistry registry) {
        OpenBaoTestContainer.register(registry);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("clinica.auth.server.issuer", () -> ISSUER);
        registry.add("clinica.auth.server.login-url", () -> LOGIN_URL);
        registry.add("clinica.auth.server.home-url", () -> HOME_URL);
        registry.add("clinica.auth.server.tokens.signing-key", () -> SIGNING_KEY);
        registry.add("clinica.auth.server.clients.api-gateway.redirect-uris", () -> REDIRECT_URI);
        registry.add("clinica.auth.server.clients.api-gateway.assertion-key", () -> CLIENT_ASSERTION_KEY);
        registry.add("clinica.auth.mail.dispatch-interval", () -> "PT1H");
    }

    public static class CapturingMailer implements Mailer {

        private final List<OutgoingMail> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(OutgoingMail mail) {
            sent.add(mail);
        }

        public List<OutgoingMail> sentTo(String email) {
            return sent.stream().filter(mail -> mail.to().equals(email)).toList();
        }

        public String lastTokenSentTo(String email) {
            List<OutgoingMail> mails = sentTo(email);
            String body = mails.getLast().body();
            int start = body.indexOf("token=") + "token=".length();
            int end = start;
            while (end < body.length() && !Character.isWhitespace(body.charAt(end))) {
                end++;
            }
            return body.substring(start, end);
        }
    }
}
