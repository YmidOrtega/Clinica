package com.ClinicaDeYmid.admissions_service.module.entity;

import com.ClinicaDeYmid.admissions_service.module.enums.TypeOfAuthorization;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "authorizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Authorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attention_id", nullable = false)
    private Attention attention;

    @Column(name = "authorization_number", nullable = false)
    private String authorizationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_of_authorization", nullable = false)
    private TypeOfAuthorization typeOfAuthorization;

    @ElementCollection
    @CollectionTable(name = "authorization_portfolio_items", joinColumns = @JoinColumn(name = "authorization_id"))
    @Column(name = "portfolio_item_id")
    private List<Long> authorizedPortfolioIds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
