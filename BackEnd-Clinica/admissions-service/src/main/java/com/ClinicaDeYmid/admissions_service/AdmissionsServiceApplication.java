package com.ClinicaDeYmid.admissions_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class AdmissionsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdmissionsServiceApplication.class, args);
    }
}
