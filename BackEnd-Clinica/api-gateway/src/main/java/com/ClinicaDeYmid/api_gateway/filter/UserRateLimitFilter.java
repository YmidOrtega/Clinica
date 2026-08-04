package com.ClinicaDeYmid.api_gateway.filter;

import com.ClinicaDeYmid.api_gateway.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Límite por usuario autenticado.
 * <p>
 * Se ejecuta <b>después</b> de {@code AuthenticationFilter}. Spring Cloud Gateway combina los
 * filtros globales y los de ruta en una única cadena ordenada, y asigna a cada filtro de ruta
 * el orden {@code índice + 1}; como {@code AuthenticationFilter} es {@code filters[0]} en
 * todas las rutas, su orden efectivo es 1. Con orden {@value #ORDER} este filtro queda por
 * detrás con margen suficiente, de modo que la identidad ya está resuelta cuando se evalúa
 * el límite.
 * <p>
 * <b>Esa fue precisamente la razón del cambio:</b> antes ambos límites vivían en un único
 * filtro global de orden 0, que leía la cabecera {@code X-User-ID} antes de que
 * {@code AuthenticationFilter} la inyectara. La rama por usuario no llegaba a activarse nunca
 * en una petición autenticada, así que en la práctica solo operaba el límite por IP.
 * <p>
 * La identidad se lee del atributo {@link GatewayAttributes#AUTHENTICATED_USER_ID} y no de la
 * cabecera: un cliente puede fabricar cabeceras, y rotar un {@code X-User-ID} falso en cada
 * petición daría un contador nuevo cada vez, vaciando de sentido el límite.
 * <p>
 * En rutas públicas no hay atributo y este filtro no hace nada; ahí la protección la aporta
 * {@link IpRateLimitFilter}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRateLimitFilter implements GlobalFilter, Ordered {

    static final int ORDER = 10;

    private final RateLimitService rateLimitService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = exchange.getAttribute(GatewayAttributes.AUTHENTICATED_USER_ID);

        if (userId == null || userId.isEmpty()) {
            log.debug("Sin usuario autenticado en el contexto; se omite el rate limit por usuario");
            return chain.filter(exchange);
        }

        log.debug("Rate limit por usuario - User: {}", userId);

        return rateLimitService.allowUser(userId)
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    }
                    log.warn("Rate limit excedido para usuario: {}", userId);
                    return rateLimitService.getRemainingForUser(userId)
                            .flatMap(remaining -> RateLimitResponse.tooManyRequests(
                                    exchange,
                                    "Límite de peticiones por usuario excedido (máx: 100/min)",
                                    remaining
                            ));
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
