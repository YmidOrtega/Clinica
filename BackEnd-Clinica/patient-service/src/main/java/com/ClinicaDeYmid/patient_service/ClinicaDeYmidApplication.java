package com.ClinicaDeYmid.patient_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;


@SpringBootApplication
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class ClinicaDeYmidApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClinicaDeYmidApplication.class, args);
	}

}
