package com.ClinicaDeYmid.commons.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

class CommonsWebAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonsWebAutoConfiguration.class));

    @Test
    void registersTheSharedHandlerInServletApplications() {
        webRunner.run(context -> assertThat(context).hasSingleBean(ApiExceptionHandler.class));
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwnHandler() {
        webRunner.withUserConfiguration(CustomHandlerConfiguration.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(ResponseEntityExceptionHandler.class)
                        .doesNotHaveBean(ApiExceptionHandler.class));
    }

    @Test
    void staysInactiveOutsideServletApplications() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonsWebAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(ApiExceptionHandler.class));
    }

    @Configuration
    static class CustomHandlerConfiguration {
        @Bean
        ResponseEntityExceptionHandler customHandler() {
            return new ResponseEntityExceptionHandler() {
            };
        }
    }
}
