// src/main/java/com/ClinicaDeYmid/auth_service/module/auth/entity/LoginAttempt.java

package com.ClinicaDeYmid.auth_service.module.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_attempts", indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_ip_address", columnList = "ip_address"),
        @Index(name = "idx_attempted_at", columnList = "attempted_at"),
        @Index(name = "idx_email_attempted", columnList = "email, attempted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @PrePersist
    protected void onCreate() {
        if (attemptedAt == null) {
            attemptedAt = LocalDateTime.now();
        }
    }
}