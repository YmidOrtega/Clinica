package com.ClinicaDeYmid.api_gateway.filter;

import com.ClinicaDeYmid.api_gateway.ratelimit.RateLimitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRateLimitFilterTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private GatewayFilterChain filterChain;

    @InjectMocks
    private UserRateLimitFilter userRateLimitFilter;

    private MockServerWebExchange exchangeWithUser(String userId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/patients").build());
        if (userId != null) {
            exchange.getAttributes().put(GatewayAttributes.AUTHENTICATED_USER_ID, userId);
        }
        return exchange;
    }

    @Test
    void filter_ShouldSkipWhenRequestIsNotAuthenticated() {
        MockServerWebExchange exchange = exchangeWithUser(null);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(userRateLimitFilter.filter(exchange, filterChain))
                .verifyComplete();

        // En rutas públicas la protección la aporta IpRateLimitFilter, no este filtro
        verifyNoInteractions(rateLimitService);
        verify(filterChain).filter(exchange);
    }

    @Test
    void filter_ShouldAllowWhenUnderLimit() {
        MockServerWebExchange exchange = exchangeWithUser("user-123");
        when(rateLimitService.allowUser("user-123")).thenReturn(Mono.just(true));
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(userRateLimitFilter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain).filter(exchange);
    }

    @Test
    void filter_ShouldRejectWithTooManyRequestsWhenOverLimit() {
        MockServerWebExchange exchange = exchangeWithUser("user-123");
        when(rateLimitService.allowUser("user-123")).thenReturn(Mono.just(false));
        when(rateLimitService.getRemainingForUser("user-123")).thenReturn(Mono.just(0L));

        StepVerifier.create(userRateLimitFilter.filter(exchange, filterChain))
                .verifyComplete();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("0", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
        assertEquals("60", exchange.getResponse().getHeaders().getFirst("Retry-After"));
        verify(filterChain, never()).filter(any());
    }

    @Test
    void filter_ShouldIgnoreSpoofedUserIdHeader() {
        // Sin atributo, una cabecera X-User-ID fabricada por el cliente no debe activar
        // ni contar en el límite por usuario.
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/patients")
                        .header("X-User-ID", "spoofed")
                        .build());
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(userRateLimitFilter.filter(exchange, filterChain))
                .verifyComplete();

        verifyNoInteractions(rateLimitService);
    }

    @Test
    void order_ShouldRunAfterRouteScopedAuthenticationFilter() {
        // AuthenticationFilter se registra como filters[0] en cada ruta; Spring Cloud Gateway
        // asigna a los filtros de ruta el orden índice+1, es decir 1. Este filtro debe quedar
        // por detrás para que la identidad ya esté resuelta, y el de IP por delante para
        // cubrir los endpoints sin autenticar.
        assertTrue(UserRateLimitFilter.ORDER > 1,
                "El rate limit por usuario debe ejecutarse después del filtro de autenticación");
        assertTrue(IpRateLimitFilter.ORDER < 1,
                "El rate limit por IP debe ejecutarse antes del filtro de autenticación");
    }
}
