package com.ClinicaDeYmid.ai_assistant_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.ClinicaDeYmid.ai_assistant_service.module.feignclient")
public class AiAssistantServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiAssistantServiceApplication.class, args);
	}
}