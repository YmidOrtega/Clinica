package com.ClinicaDeYmid.clients_service.module.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "portfolios",
        indexes = {
                @Index(name = "idx_portfolios_code_cups", columnList = "code_cups"),
                @Index(name = "idx_portfolios_code_clinic", columnList = "code_clinic"),
                @Index(name = "idx_portfolios_name", columnList = "name")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;
    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "code_cups",nullable = false)
    private String codeCups;
    @Column(name = "code_clinic",nullable = false)
    private String codeClinic;
    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @ManyToMany(mappedBy = "coveredServices", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Contract> contracts = new ArrayList<>();

}
