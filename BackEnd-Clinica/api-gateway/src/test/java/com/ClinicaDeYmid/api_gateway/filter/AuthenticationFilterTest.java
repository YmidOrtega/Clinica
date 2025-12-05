package com.ClinicaDeYmid.api_gateway.filter;

import com.ClinicaDeYmid.api_gateway.security.JwtValidatorService;
import com.ClinicaDeYmid.api_gateway.security.TokenBlacklistServiceGateway;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Claim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

    @Mock
    private RouteValidator routeValidator;

    @Mock
    private JwtValidatorService jwtValidatorService;

    @Mock
    private TokenBlacklistServiceGateway tokenBlacklistServiceGateway;

    @Mock
    private GatewayFilterChain filterChain;

    @InjectMocks
    private AuthenticationFilter authenticationFilter;

    @BeforeEach
    void setUp() {
        // Reset mocks if necessary
    }

    @Test
    void filter_ShouldAllowOpenEndpoints() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(routeValidator.isSecured(any())).thenReturn(false);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, filterChain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(routeValidator).isSecured(any());
        verify(filterChain).filter(exchange);
        verifyNoInteractions(jwtValidatorService, tokenBlacklistServiceGateway);
    }

    @Test
    void filter_ShouldBlockMissingAuthorizationHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/secured").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(routeValidator.isSecured(any())).thenReturn(true);

        Mono<Void> result = authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, filterChain);

        StepVerifier.create(result)
                .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
        verify(filterChain, never()).filter(any());
    }

    @Test
    void filter_ShouldBlockInvalidTokenFormat() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/secured")
                .header(HttpHeaders.AUTHORIZATION, "InvalidToken")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(routeValidator.isSecured(any())).thenReturn(true);

        Mono<Void> result = authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, filterChain);

        StepVerifier.create(result)
                .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
        verify(filterChain, never()).filter(any());
    }

    @Test
    void filter_ShouldAllowValidToken() {
        String validToken = "valid.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/secured")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        Claim claim = mock(Claim.class);
        when(decodedJWT.getSubject()).thenReturn("123");
        when(decodedJWT.getClaim("email")).thenReturn(claim);
        when(claim.asString()).thenReturn("user@example.com");

        when(routeValidator.isSecured(any())).thenReturn(true);
        when(jwtValidatorService.validateAndDecodeToken(validToken)).thenReturn(Mono.just(decodedJWT));
        when(tokenBlacklistServiceGateway.isTokenBlacklisted(validToken)).thenReturn(Mono.just(false));
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, filterChain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(filterChain).filter(any(ServerWebExchange.class));
    }

    @Test
    void filter_ShouldBlockBlacklistedToken() {
        String blacklistedToken = "blacklisted.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/secured")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + blacklistedToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        DecodedJWT decodedJWT = mock(DecodedJWT.class); // Mock needed even if blacklisted logic comes later in chain?
        // Actually, implementation calls validateAndDecodeToken first.
        
        when(routeValidator.isSecured(any())).thenReturn(true);
        when(jwtValidatorService.validateAndDecodeToken(blacklistedToken)).thenReturn(Mono.just(decodedJWT));
        when(tokenBlacklistServiceGateway.isTokenBlacklisted(blacklistedToken)).thenReturn(Mono.just(true));

        Mono<Void> result = authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, filterChain);

        StepVerifier.create(result)
                .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
        verify(filterChain, never()).filter(any());
    }
    
    @Test
    void filter_ShouldHandleAuthServiceUnavailable() {
        String token = "some.token";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/secured")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(routeValidator.isSecured(any())).thenReturn(true);
        when(jwtValidatorService.validateAndDecodeToken(token))
                .thenReturn(Mono.error(new RuntimeException("Auth-Service está disponible... error message"))); // Matches the catch logic

        Mono<Void> result = authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, filterChain);

        StepVerifier.create(result)
                .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE;
    }
}