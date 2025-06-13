package com.ClinicaDeYmid.admissions_service.module.entity;

import com.ClinicaDeYmid.admissions_service.module.enums.UserActionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "attention_user_history")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AttentionUserHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attention_id", nullable = false)
    private Attention attention;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private UserActionType actionType;

    @CreationTimestamp
    @Column(name = "action_timestamp", nullable = false, updatable = false)
    private LocalDateTime actionTimestamp;

    @Column(name = "observations", length = 500)
    private String observations;
}
