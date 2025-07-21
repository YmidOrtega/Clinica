package com.ClinicaDeYmid.ai_assistant_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class AiAssistantServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiAssistantServiceApplication.class, args);
	}

}
