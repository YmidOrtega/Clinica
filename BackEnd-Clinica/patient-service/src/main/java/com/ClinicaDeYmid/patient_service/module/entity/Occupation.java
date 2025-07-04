package com.ClinicaDeYmid.patient_service.module.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "occupations")
@Data
@EqualsAndHashCode(of = "id")
public class Occupation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String description;

    @Column (name = "created_at")
    private LocalDateTime createdAt;
    @Column (name = "updated_at")
    private LocalDateTime updatedAt;

}
