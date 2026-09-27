package com.ClinicaDeYmid.api_gateway.infrastructure.routing;

import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchangeHandlerFunction;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

class SlowRouteProxy implements HandlerFunction<ServerResponse>, ApplicationListener<ContextRefreshedEvent> {

    private final ProxyExchangeHandlerFunction proxy;

    SlowRouteProxy(ProxyExchangeHandlerFunction proxy) {
        this.proxy = proxy;
    }

    @Override
    public ServerResponse handle(ServerRequest request) throws Exception {
        return proxy.handle(request);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        proxy.onApplicationEvent(event);
    }
}
