package com.ClinicaDeYmid.api_gateway.filter;

import com.ClinicaDeYmid.api_gateway.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Límite por dirección IP.
 * <p>
 * Se ejecuta <b>antes</b> de la autenticación (orden {@value #ORDER}, mientras que
 * {@code AuthenticationFilter} se registra como filtro de ruta y recibe orden 1). Ese orden
 * es intencionado: este límite tiene que cubrir precisamente los endpoints donde todavía no
 * hay usuario —el login por encima de todo—, que son el objetivo natural de un ataque de
 * fuerza bruta. Si se aplicara después de validar el token, un atacante que nunca llega a
 * autenticarse jamás lo alcanzaría.
 * <p>
 * El umbral es alto ({@code 1000/min}) porque tras un NAT corporativo hay muchos usuarios
 * legítimos compartiendo la misma IP de salida.
 *
 * @see UserRateLimitFilter límite por usuario, aplicado después de autenticar
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpRateLimitFilter implements GlobalFilter, Ordered {

    static final int ORDER = 0;

    private final RateLimitService rateLimitService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ipAddress = getClientIp(exchange.getRequest());

        log.debug("Rate limit por IP - IP: {}", ipAddress);

        return rateLimitService.allowIp(ipAddress)
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    }
                    log.warn("Rate limit excedido para IP: {}", ipAddress);
                    return rateLimitService.getRemainingForIp(ipAddress)
                            .flatMap(remaining -> RateLimitResponse.tooManyRequests(
                                    exchange,
                                    "Límite de peticiones por IP excedido (máx: 1000/min)",
                                    remaining
                            ));
                });
    }

    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
