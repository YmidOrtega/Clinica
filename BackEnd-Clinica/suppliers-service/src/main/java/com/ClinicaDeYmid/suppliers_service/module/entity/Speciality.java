package com.ClinicaDeYmid.suppliers_service.module.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Entity
@Table(name = "specialties")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"doctors", "subSpecialties"})
public class Speciality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(unique = true, nullable = false)
    private int codeSpeciality;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToMany(mappedBy = "specialties", fetch = FetchType.LAZY)
    @JsonBackReference("doctor-specialties")
    private Set<Doctor> doctors;

    @OneToMany(mappedBy = "speciality", cascade = CascadeType.MERGE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference("speciality-subspecialties")
    private Set<SubSpecialty> subSpecialties;

    @Builder.Default
    private Boolean active = true;
}