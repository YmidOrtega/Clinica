package com.ClinicaDeYmid.suppliers_service;

import jakarta.persistence.Column;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@Configuration
public class SuppliersServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SuppliersServiceApplication.class, args);
	}

}
