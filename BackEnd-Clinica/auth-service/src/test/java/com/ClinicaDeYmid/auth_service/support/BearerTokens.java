package com.ClinicaDeYmid.auth_service.support;

import com.ClinicaDeYmid.auth_service.domain.user.User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BearerTokens {

    private final JwtEncoder encoder;

    public BearerTokens(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String fresh(User user) {
        return issue(user, claims -> {
        });
    }

    public String authenticatedAgo(User user, Duration age) {
        return issue(user, claims -> claims.claim("auth_time", Instant.now().minus(age).truncatedTo(ChronoUnit.SECONDS)));
    }

    public String issue(User user, Consumer<JwtClaimsSet.Builder> customizer) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(AuthTestSupport.ISSUER)
                .subject(user.uuid().toString())
                .audience(new ArrayList<>(List.of("clinica-api")))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(5)))
                .claim("role", user.role().name())
                .claim("auth_time", now)
                .claim("amr", new ArrayList<>(List.of("pwd", "otp", "mfa")));
        customizer.accept(claims);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.ES256).build(), claims.build())).getTokenValue();
    }
}
