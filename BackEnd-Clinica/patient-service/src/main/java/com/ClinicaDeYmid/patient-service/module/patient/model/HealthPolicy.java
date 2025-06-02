package com.ClinicaDeYmid.module.patient.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Entity
@Table(name = "health_policies")
@Data
@EqualsAndHashCode(of = "id")
public class HealthPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "social_reason", nullable = false)
    private String socialReason;
    private int nit;
    private String contract;
    @Column (name = "number_contract")
    private String numberContract;
    private String type;
    private String address;
    private String phone;
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
