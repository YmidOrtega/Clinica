package com.ClinicaDeYmid.admissions_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.ClinicaDeYmid.admissions_service.module.feignclient")
@EnableDiscoveryClient
@EnableCaching
public class AdmissionsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AdmissionsServiceApplication.class, args);
	}

}
