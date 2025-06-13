package com.ClinicaDeYmid.admissions_service.module.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "attention_movements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttentionMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Attention attention;

    @ManyToOne
    private ConfigurationService fromConfiguration;

    @ManyToOne
    private ConfigurationService toConfiguration;

    @Column(nullable = false)
    private LocalDateTime movedAt;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private Long userId;
}
