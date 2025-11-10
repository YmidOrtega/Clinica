package com.ClinicaDeYmid.api_gateway.filter;

import com.ClinicaDeYmid.api_gateway.security.JwtValidatorService;
import com.ClinicaDeYmid.api_gateway.security.TokenBlacklistServiceGateway;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticationFilterTest {

    @Mock
    private RouteValidator routeValidator;
    @Mock
    private JwtValidatorService jwtValidatorService;
    @Mock
    private TokenBlacklistServiceGateway tokenBlacklistServiceGateway;
    @Mock
    private ServerWebExchange exchange;
    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpResponse response;
    @Mock
    private GatewayFilterChain chain;
    @Mock
    private HttpHeaders headers;
    @Mock
    private DecodedJWT decodedJWT;
    @Mock
    private ServerHttpRequest.Builder requestBuilder;
    @Mock
    private DefaultDataBufferFactory dataBufferFactory; // Mock the factory
    @Mock
    private DefaultDataBuffer dataBuffer; // Mock the data buffer

    @InjectMocks
    private AuthenticationFilter authenticationFilter;

    @BeforeEach
    void setUp() {
        // No global stubs for bufferFactory and wrap here, they will be moved to specific tests that need them.
    }

    @Test
    @DisplayName("Debería permitir el paso para rutas no seguras")
    void apply_shouldAllowUnsecuredRoutes() {
        // Arrange
        when(exchange.getRequest()).thenReturn(request);
        when(request.getURI()).thenReturn(URI.create("/public"));
        when(routeValidator.isSecured(request)).thenReturn(false); // Mock the method's behavior
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // Act
        authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, chain).block();

        // Assert
        verify(chain).filter(exchange);
        verify(response, never()).setStatusCode(any());
    }

    @Test
    @DisplayName("Debería devolver 401 si falta el encabezado de autorización en ruta segura")
    void apply_shouldReturn401_whenAuthorizationHeaderIsMissing() {
        // Arrange
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(request.getURI()).thenReturn(URI.create("/secured"));
        when(routeValidator.isSecured(request)).thenReturn(true); // Mock the method's behavior
        when(request.getHeaders()).thenReturn(headers);
        when(headers.containsKey(HttpHeaders.AUTHORIZATION)).thenReturn(false);
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(response.bufferFactory()).thenReturn(dataBufferFactory);
        when(dataBufferFactory.wrap(any(byte[].class))).thenReturn(dataBuffer);
        when(response.writeWith(any())).thenReturn(Mono.empty());

        // Act
        authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, chain).block();

        // Assert
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Debería devolver 401 si el formato del token es inválido")
    void apply_shouldReturn401_whenTokenFormatIsInvalid() {
        // Arrange
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(request.getURI()).thenReturn(URI.create("/secured"));
        when(routeValidator.isSecured(request)).thenReturn(true); // Mock the method's behavior
        when(request.getHeaders()).thenReturn(headers);
        when(headers.containsKey(HttpHeaders.AUTHORIZATION)).thenReturn(true);
        when(headers.getFirst(HttpHeaders.AUTHORIZATION)).thenReturn("InvalidToken");
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(response.bufferFactory()).thenReturn(dataBufferFactory);
        when(dataBufferFactory.wrap(any(byte[].class))).thenReturn(dataBuffer);
        when(response.writeWith(any())).thenReturn(Mono.empty());

        // Act
        authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, chain).block();

        // Assert
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Debería devolver 401 si el token JWT es inválido")
    void apply_shouldReturn401_whenJwtTokenIsInvalid() {
        // Arrange
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(request.getURI()).thenReturn(URI.create("/secured"));
        when(routeValidator.isSecured(request)).thenReturn(true); // Mock the method's behavior
        when(request.getHeaders()).thenReturn(headers);
        when(headers.containsKey(HttpHeaders.AUTHORIZATION)).thenReturn(true);
        when(headers.getFirst(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer invalid.jwt.token");
        when(jwtValidatorService.validateAndDecodeToken(anyString())).thenReturn(Mono.error(new RuntimeException("Invalid JWT")));
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(response.bufferFactory()).thenReturn(dataBufferFactory);
        when(dataBufferFactory.wrap(any(byte[].class))).thenReturn(dataBuffer);
        when(response.writeWith(any())).thenReturn(Mono.empty());

        // Act
        authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, chain).block();

        // Assert
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Debería devolver 401 si el token está en la lista negra")
    void apply_shouldReturn401_whenTokenIsBlacklisted() {
        // Arrange
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(request.getURI()).thenReturn(URI.create("/secured"));
        when(routeValidator.isSecured(request)).thenReturn(true); // Mock the method's behavior
        when(request.getHeaders()).thenReturn(headers);
        when(headers.containsKey(HttpHeaders.AUTHORIZATION)).thenReturn(true);
        when(headers.getFirst(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid.jwt.token");
        when(jwtValidatorService.validateAndDecodeToken(anyString())).thenReturn(Mono.just(decodedJWT));
        when(tokenBlacklistServiceGateway.isTokenBlacklisted(anyString())).thenReturn(Mono.just(true));
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(response.bufferFactory()).thenReturn(dataBufferFactory);
        when(dataBufferFactory.wrap(any(byte[].class))).thenReturn(dataBuffer);
        when(response.writeWith(any())).thenReturn(Mono.empty());

        // Act
        authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, chain).block();

        // Assert
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Debería devolver 503 si Auth-Service no está disponible")
    void apply_shouldReturn503_whenAuthServiceIsUnavailable() {
        // Arrange
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(request.getURI()).thenReturn(URI.create("/secured"));
        when(routeValidator.isSecured(request)).thenReturn(true); // Mock the method's behavior
        when(request.getHeaders()).thenReturn(headers);
        when(headers.containsKey(HttpHeaders.AUTHORIZATION)).thenReturn(true);
        when(headers.getFirst(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid.jwt.token");
        when(jwtValidatorService.validateAndDecodeToken(anyString())).thenReturn(Mono.error(new RuntimeException("Auth-Service is unavailable")));
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(response.bufferFactory()).thenReturn(dataBufferFactory);
        when(dataBufferFactory.wrap(any(byte[].class))).thenReturn(dataBuffer);
        when(response.writeWith(any())).thenReturn(Mono.empty());

        // Act
        authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, chain).block();

        // Assert
        verify(response).setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Debería autenticar exitosamente y mutar la solicitud")
    void apply_shouldAuthenticateSuccessfullyAndMutateRequest() {
        // Arrange
        String userId = "123";
        String userEmail = "test@example.com";
        String token = "valid.jwt.token";

        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(request.getURI()).thenReturn(URI.create("/secured"));
        when(routeValidator.isSecured(request)).thenReturn(true); // Mock the method's behavior
        when(request.getHeaders()).thenReturn(headers);
        when(headers.containsKey(HttpHeaders.AUTHORIZATION)).thenReturn(true);
        when(headers.getFirst(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(jwtValidatorService.validateAndDecodeToken(token)).thenReturn(Mono.just(decodedJWT));
        when(tokenBlacklistServiceGateway.isTokenBlacklisted(token)).thenReturn(Mono.just(false));
        when(decodedJWT.getSubject()).thenReturn(userId);
        when(decodedJWT.getClaim("email")).thenReturn(mock(com.auth0.jwt.interfaces.Claim.class));
        when(decodedJWT.getClaim("email").asString()).thenReturn(userEmail);

        // Mock request mutation
        when(request.mutate()).thenReturn(requestBuilder);
        when(requestBuilder.header("X-User-ID", userId)).thenReturn(requestBuilder);
        when(requestBuilder.header("X-User-Email", userEmail)).thenReturn(requestBuilder);
        lenient().when(requestBuilder.build()).thenReturn(request); // Return the original mock request for simplicity
        // Correctly mock the exchange mutation chain
        ServerWebExchange.Builder exchangeBuilderMock = mock(ServerWebExchange.Builder.class);
        when(exchange.mutate()).thenReturn(exchangeBuilderMock);
        when(exchangeBuilderMock.request(any(ServerHttpRequest.class))).thenReturn(exchangeBuilderMock);
        when(exchangeBuilderMock.build()).thenReturn(exchange); // Return the original exchange mock
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // Act
        authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, chain).block();

        // Assert
        verify(requestBuilder).header("X-User-ID", userId);
        verify(requestBuilder).header("X-User-Email", userEmail);
        verify(chain).filter(any(ServerWebExchange.class)); // Verify filter is called with mutated exchange
        verify(response, never()).setStatusCode(any());
    }
}
