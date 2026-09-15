package com.ClinicaDeYmid.auth_service.infrastructure.security;

import com.ClinicaDeYmid.commons.openbao.transit.KeyVersion;
import com.ClinicaDeYmid.commons.openbao.transit.SignatureFormat;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;

import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

final class TransitJwtEncoder implements JwtEncoder {

    private final TransitKeys keys;

    TransitJwtEncoder(TransitKeys keys) {
        this.keys = keys;
        keys.current().requireType(TransitJwkSource.KEY_TYPE);
    }

    @Override
    public Jwt encode(JwtEncoderParameters parameters) {
        KeyVersion version = keys.current().latest();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).keyID(version.id()).build();
        JwtClaimsSet claims = parameters.getClaims();
        Payload payload = new Payload(toNimbus(claims).toJSONObject());
        JWSObject unsigned = new JWSObject(header, payload);
        byte[] signature;
        try {
            signature = keys.client().sign(version, unsigned.getSigningInput(), SignatureFormat.JWS);
        } catch (RuntimeException failure) {
            throw new JwtEncodingException("Could not sign the token with " + version.id(), failure);
        }
        String token = header.toBase64URL() + "." + payload.toBase64URL() + "." + Base64URL.encode(signature);
        return new Jwt(token, claims.getIssuedAt(), claims.getExpiresAt(),
                Map.of("alg", JWSAlgorithm.ES256.getName(), "typ", JOSEObjectType.JWT.getType(), "kid", version.id()), claims.getClaims());
    }

    private static JWTClaimsSet toNimbus(JwtClaimsSet claims) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        claims.getClaims().forEach((name, value) -> builder.claim(name, switch (value) {
            case Instant instant -> Date.from(instant);
            case URL url -> url.toExternalForm();
            default -> value;
        }));
        return builder.build();
    }
}
