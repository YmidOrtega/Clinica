package com.ClinicaDeYmid.api_gateway.filter;

/**
 * Atributos que los filtros del gateway se pasan entre sí a través del
 * {@code ServerWebExchange}.
 * <p>
 * Se usan atributos y no cabeceras porque el cliente no puede fabricarlos: una cabecera
 * {@code X-User-ID} entrante es texto bajo control de quien llama, mientras que un atributo
 * solo lo escribe un filtro de este proceso. La cabecera se sigue enviando al servicio
 * destino, pero las decisiones internas del gateway se toman sobre el atributo.
 */
public final class GatewayAttributes {

    /** Identificador del usuario extraído del JWT ya verificado. */
    public static final String AUTHENTICATED_USER_ID = "clinica.gateway.authenticatedUserId";

    private GatewayAttributes() {}
}
