package com.ClinicaDeYmid.clinical_history_service.infrastructure.json;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JsonConfiguration {

    @Bean
    NoteContentJsonModule noteContentJsonModule() {
        return new NoteContentJsonModule();
    }
}
