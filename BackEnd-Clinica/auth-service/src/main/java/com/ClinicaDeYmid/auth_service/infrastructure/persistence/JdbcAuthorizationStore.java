package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.application.audit.SecurityAuditLog;
import com.ClinicaDeYmid.auth_service.application.audit.SecurityEvent;
import com.ClinicaDeYmid.auth_service.application.session.SessionRevocation;
import com.ClinicaDeYmid.auth_service.infrastructure.security.StaffAuthentication;
import com.ClinicaDeYmid.auth_service.infrastructure.security.StaffPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcAuthorizationStore implements OAuth2AuthorizationService, SessionRevocation {

    static final int MAX_SESSIONS_PER_USER = 5;

    private static final Logger log = LoggerFactory.getLogger(JdbcAuthorizationStore.class);
    private static final String STATE = OAuth2ParameterNames.STATE;
    private static final String ACCESS_TOKEN_SCOPES = "clinica.access_token.scopes";
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final RegisteredClientRepository clients;
    private final TransactionOperations transactions;
    private final SecurityAuditLog audit;
    private final Clock clock;
    private final ObjectMapper oauthJson;
    private final ObjectMapper principalJson = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    public JdbcAuthorizationStore(JdbcTemplate jdbc, RegisteredClientRepository clients, TransactionOperations transactions, SecurityAuditLog audit,
                                  Clock clock) {
        this.jdbc = jdbc;
        this.clients = clients;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
        ClassLoader classLoader = JdbcAuthorizationStore.class.getClassLoader();
        this.oauthJson = new ObjectMapper();
        this.oauthJson.registerModules(SecurityJackson2Modules.getModules(classLoader));
        this.oauthJson.registerModule(new OAuth2AuthorizationServerJackson2Module());
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        transactions.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            boolean exists = !jdbc.queryForList("SELECT id FROM auth_sessions.authorizations WHERE id = ? FOR UPDATE", String.class,
                    authorization.getId()).isEmpty();
            String state = authorization.getAttribute(STATE);
            Object[] values = {authorization.getRegisteredClientId(), authorization.getPrincipalName(),
                    authorization.getAuthorizationGrantType().getValue(),
                    StringUtils.collectionToCommaDelimitedString(authorization.getAuthorizedScopes()), principal(authorization),
                    attributes(authorization), state == null ? null : TokenHashes.of(state), Timestamp.from(now), authorization.getId()};
            if (exists) {
                jdbc.update("""
                        UPDATE auth_sessions.authorizations SET registered_client_id = ?, principal_name = ?, grant_type = ?,
                            authorized_scopes = ?, principal = ?, attributes = ?, state_hash = ?, updated_at = ? WHERE id = ?""", values);
            } else {
                jdbc.update("""
                        INSERT INTO auth_sessions.authorizations (registered_client_id, principal_name, grant_type, authorized_scopes,
                            principal, attributes, state_hash, updated_at, id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        append(values, Timestamp.from(now)));
            }
            replaceTokens(authorization, now);
            if (authorization.getRefreshToken() != null) {
                enforceSessionLimit(authorization.getPrincipalName(), now);
            }
        });
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        jdbc.update("DELETE FROM auth_sessions.authorizations WHERE id = ?", authorization.getId());
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return load(id, null, null).orElse(null);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        String hash = TokenHashes.of(token);
        if (tokenType != null && STATE.equals(tokenType.getValue())) {
            return jdbc.queryForList("SELECT id FROM auth_sessions.authorizations WHERE state_hash = ?", String.class, hash).stream()
                    .findFirst().flatMap(id -> load(id, null, null)).orElse(null);
        }
        List<Map<String, Object>> matches = jdbc.queryForList(
                "SELECT authorization_id, token_type FROM auth_sessions.authorization_tokens WHERE value_hash = ?", hash);
        Optional<Map<String, Object>> match = matches.stream()
                .filter(row -> tokenType == null || tokenType.getValue().equals(row.get("token_type")))
                .findFirst();
        if (match.isPresent()) {
            return load((String) match.get().get("authorization_id"), (String) match.get().get("token_type"), token).orElse(null);
        }
        if (tokenType == null || OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            revokeFamilyOfReusedRefreshToken(hash);
        }
        return null;
    }

    @Override
    public int revokeAll(UUID userUuid) {
        int revoked = jdbc.update("DELETE FROM auth_sessions.authorizations WHERE principal_name = ?", userUuid.toString());
        jdbc.update("DELETE FROM auth_sessions.SPRING_SESSION WHERE PRINCIPAL_NAME = ?", userUuid.toString());
        return revoked;
    }

    private void revokeFamilyOfReusedRefreshToken(String hash) {
        jdbc.queryForList("SELECT authorization_id FROM auth_sessions.rotated_refresh_tokens WHERE value_hash = ?", String.class, hash)
                .stream().findFirst().ifPresent(authorizationId -> transactions.executeWithoutResult(status -> {
                    String principalName = jdbc.queryForList("SELECT principal_name FROM auth_sessions.authorizations WHERE id = ?", String.class,
                            authorizationId).stream().findFirst().orElse(null);
                    int removed = jdbc.update("DELETE FROM auth_sessions.authorizations WHERE id = ?", authorizationId);
                    if (removed > 0) {
                        audit.record(new SecurityEvent.RefreshTokenReuseDetected(UUID.fromString(principalName), authorizationId));
                        log.warn("A rotated refresh token was reused; revoked authorization {} and all its tokens", authorizationId);
                    }
                }));
    }

    private void replaceTokens(OAuth2Authorization authorization, Instant now) {
        String previousRefresh = jdbc.queryForList("""
                SELECT value_hash FROM auth_sessions.authorization_tokens WHERE authorization_id = ? AND token_type = ?""", String.class,
                authorization.getId(), OAuth2TokenType.REFRESH_TOKEN.getValue()).stream().findFirst().orElse(null);
        jdbc.update("DELETE FROM auth_sessions.authorization_tokens WHERE authorization_id = ?", authorization.getId());
        for (TokenKind kind : TokenKind.values()) {
            OAuth2Authorization.Token<? extends OAuth2Token> token = authorization.getToken(kind.tokenClass);
            if (token == null) {
                continue;
            }
            Map<String, Object> metadata = new HashMap<>(token.getMetadata());
            if (token.getToken() instanceof OAuth2AccessToken accessToken) {
                metadata.put(ACCESS_TOKEN_SCOPES, new ArrayList<>(accessToken.getScopes()));
            }
            jdbc.update("""
                    INSERT INTO auth_sessions.authorization_tokens
                        (authorization_id, token_type, token_class, value_hash, issued_at, expires_at, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""", authorization.getId(), kind.type, kind.tokenClass.getName(),
                    TokenHashes.of(token.getToken().getTokenValue()), timestamp(token.getToken().getIssuedAt()),
                    timestamp(token.getToken().getExpiresAt()), write(oauthJson, metadata));
        }
        OAuth2Authorization.Token<OAuth2RefreshToken> refresh = authorization.getRefreshToken();
        String currentRefresh = refresh == null ? null : TokenHashes.of(refresh.getToken().getTokenValue());
        if (previousRefresh != null && !previousRefresh.equals(currentRefresh)) {
            jdbc.update("INSERT IGNORE INTO auth_sessions.rotated_refresh_tokens (value_hash, authorization_id, rotated_at) VALUES (?, ?, ?)",
                    previousRefresh, authorization.getId(), Timestamp.from(now));
        }
    }

    private void enforceSessionLimit(String principalName, Instant now) {
        List<String> surplus = jdbc.queryForList("""
                SELECT a.id FROM auth_sessions.authorizations a
                JOIN auth_sessions.authorization_tokens t ON t.authorization_id = a.id AND t.token_type = ? AND t.expires_at > ?
                WHERE a.principal_name = ?
                ORDER BY a.created_at DESC, a.id DESC
                LIMIT 1000 OFFSET ?""", String.class, OAuth2TokenType.REFRESH_TOKEN.getValue(), Timestamp.from(now), principalName,
                MAX_SESSIONS_PER_USER);
        surplus.forEach(id -> jdbc.update("DELETE FROM auth_sessions.authorizations WHERE id = ?", id));
        if (!surplus.isEmpty()) {
            log.info("Closed {} oldest sessions of {} beyond the limit of {}", surplus.size(), principalName, MAX_SESSIONS_PER_USER);
        }
    }

    private Optional<OAuth2Authorization> load(String id, String matchedType, String matchedValue) {
        return jdbc.query("SELECT * FROM auth_sessions.authorizations WHERE id = ?", (row, index) -> row(row, matchedType, matchedValue), id)
                .stream().findFirst();
    }

    private OAuth2Authorization row(ResultSet row, String matchedType, String matchedValue) throws SQLException {
        String registeredClientId = row.getString("registered_client_id");
        RegisteredClient client = clients.findById(registeredClientId);
        if (client == null) {
            throw new IllegalStateException("The registered client " + registeredClientId + " no longer exists");
        }
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client)
                .id(row.getString("id"))
                .principalName(row.getString("principal_name"))
                .authorizationGrantType(new AuthorizationGrantType(row.getString("grant_type")))
                .authorizedScopes(StringUtils.commaDelimitedListToSet(row.getString("authorized_scopes")));
        String attributes = row.getString("attributes");
        if (attributes != null) {
            Map<String, Object> values = read(oauthJson, attributes);
            builder.attributes(map -> map.putAll(values));
        }
        String principal = row.getString("principal");
        if (principal != null) {
            builder.attribute(Principal.class.getName(), new StaffAuthentication(read(principalJson, principal, StaffPrincipal.class)));
        }
        String stateHash = row.getString("state_hash");
        if (stateHash != null) {
            builder.attribute(STATE, TokenHashes.placeholder(stateHash));
        }
        jdbc.query("SELECT * FROM auth_sessions.authorization_tokens WHERE authorization_id = ?", tokenRow -> {
            String type = tokenRow.getString("token_type");
            String value = type.equals(matchedType) ? matchedValue : TokenHashes.placeholder(tokenRow.getString("value_hash"));
            Map<String, Object> metadata = read(oauthJson, tokenRow.getString("metadata"));
            TokenKind.of(type).addTo(builder, value, instant(tokenRow.getTimestamp("issued_at")), instant(tokenRow.getTimestamp("expires_at")),
                    metadata);
        }, row.getString("id"));
        return builder.build();
    }

    private String principal(OAuth2Authorization authorization) {
        Object principal = authorization.getAttribute(Principal.class.getName());
        return principal instanceof StaffAuthentication staff ? write(principalJson, staff.getPrincipal()) : null;
    }

    private String attributes(OAuth2Authorization authorization) {
        Map<String, Object> attributes = new HashMap<>(authorization.getAttributes());
        attributes.remove(Principal.class.getName());
        attributes.remove(STATE);
        return attributes.isEmpty() ? null : write(oauthJson, attributes);
    }

    private static Object[] append(Object[] values, Object extra) {
        Object[] extended = Arrays.copyOf(values, values.length + 1);
        extended[values.length] = extra;
        return extended;
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String write(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize an authorization", ex);
        }
    }

    private static Map<String, Object> read(ObjectMapper mapper, String json) {
        try {
            return json == null ? Map.of() : mapper.readValue(json, MAP);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read a stored authorization", ex);
        }
    }

    private static <T> T read(ObjectMapper mapper, String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read a stored principal", ex);
        }
    }

    private enum TokenKind {

        AUTHORIZATION_CODE(OAuth2ParameterNames.CODE, OAuth2AuthorizationCode.class),
        ACCESS_TOKEN(OAuth2TokenType.ACCESS_TOKEN.getValue(), OAuth2AccessToken.class),
        REFRESH_TOKEN(OAuth2TokenType.REFRESH_TOKEN.getValue(), OAuth2RefreshToken.class),
        ID_TOKEN("id_token", OidcIdToken.class);

        private final String type;
        private final Class<? extends OAuth2Token> tokenClass;

        TokenKind(String type, Class<? extends OAuth2Token> tokenClass) {
            this.type = type;
            this.tokenClass = tokenClass;
        }

        static TokenKind of(String type) {
            for (TokenKind kind : values()) {
                if (kind.type.equals(type)) {
                    return kind;
                }
            }
            throw new IllegalStateException("Unsupported stored token type " + type);
        }

        @SuppressWarnings("unchecked")
        void addTo(OAuth2Authorization.Builder builder, String value, Instant issuedAt, Instant expiresAt, Map<String, Object> metadata) {
            Map<String, Object> stored = new HashMap<>(metadata);
            OAuth2Token token = switch (this) {
                case AUTHORIZATION_CODE -> new OAuth2AuthorizationCode(value, issuedAt, expiresAt);
                case ACCESS_TOKEN -> {
                    Collection<String> scopes = (Collection<String>) stored.remove(ACCESS_TOKEN_SCOPES);
                    yield new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, value, issuedAt, expiresAt,
                            scopes == null ? Set.of() : new HashSet<>(scopes));
                }
                case REFRESH_TOKEN -> new OAuth2RefreshToken(value, issuedAt, expiresAt);
                case ID_TOKEN -> new OidcIdToken(value, issuedAt, expiresAt,
                        (Map<String, Object>) stored.get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME));
            };
            builder.token(token, map -> map.putAll(stored));
        }
    }
}
