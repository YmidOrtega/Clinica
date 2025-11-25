package com.ClinicaDeYmid.admissions_service.module.feignclient;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Slf4j
public class FeignClientInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null) {
                log.debug("Propagating Authorization header to Feign request: {}", template.url());
                template.header("Authorization", authHeader);
            } else {
                log.warn("No Authorization header found in request context for Feign call to: {}", template.url());
            }
        } else {
            log.warn("No ServletRequestAttributes available. Cannot propagate Authorization header to: {}", template.url());
        }
    }
}
