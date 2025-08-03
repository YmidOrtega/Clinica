package com.ClinicaDeYmid.suppliers_service.module.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sub_specialties")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubSpecialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private int codeSubSpecialty;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "speciality_id")
    private Speciality speciality;
}

