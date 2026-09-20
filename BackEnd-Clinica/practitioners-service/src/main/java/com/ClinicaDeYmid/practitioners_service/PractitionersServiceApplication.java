package com.ClinicaDeYmid.practitioners_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class PractitionersServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PractitionersServiceApplication.class, args);
    }
}
