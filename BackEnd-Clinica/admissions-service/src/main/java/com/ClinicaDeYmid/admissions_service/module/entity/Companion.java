package com.ClinicaDeYmid.admissions_service.module.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Companion {

    private String fullName;

    private String phoneNumber;

    private String relationship;

}