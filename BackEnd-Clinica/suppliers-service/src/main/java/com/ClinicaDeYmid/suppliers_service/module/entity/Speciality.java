package com.ClinicaDeYmid.suppliers_service.module.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "specialties")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Speciality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private int codeSpeciality;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToMany(mappedBy = "specialties")
    private List<Doctor> doctors;

    @OneToMany(mappedBy = "speciality", cascade = CascadeType.MERGE, orphanRemoval = true)
    private List<SubSpecialty> subSpecialties;

    @Builder.Default
    private Boolean active = true;
}

