package com.ClinicaDeYmid.suppliers_service.module.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "sub_specialties")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"speciality", "doctors"})
public class SubSpecialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(unique = true, nullable = false)
    private int codeSubSpecialty;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "speciality_id")
    @JsonBackReference("speciality-subspecialties") // Evita la serialización circular con Speciality
    private Speciality speciality;

    @ManyToMany(mappedBy = "subSpecialties", fetch = FetchType.LAZY)
    @JsonBackReference("doctor-subspecialties") // Evita la serialización circular con Doctor
    private Set<Doctor> doctors = new HashSet<>();
}