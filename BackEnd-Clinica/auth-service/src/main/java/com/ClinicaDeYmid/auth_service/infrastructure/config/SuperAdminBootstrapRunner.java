package com.ClinicaDeYmid.auth_service.infrastructure.config;

import com.ClinicaDeYmid.auth_service.application.account.SuperAdminBootstrap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class SuperAdminBootstrapRunner implements ApplicationRunner {

    private final SuperAdminBootstrap bootstrap;
    private final String email;
    private final String fullName;

    SuperAdminBootstrapRunner(SuperAdminBootstrap bootstrap, @Value("${clinica.auth.bootstrap.super-admin-email:}") String email,
                              @Value("${clinica.auth.bootstrap.super-admin-name:Administrador Inicial}") String fullName) {
        this.bootstrap = bootstrap;
        this.email = email;
        this.fullName = fullName;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!email.isBlank()) {
            bootstrap.ensure(email, fullName);
        }
    }
}
