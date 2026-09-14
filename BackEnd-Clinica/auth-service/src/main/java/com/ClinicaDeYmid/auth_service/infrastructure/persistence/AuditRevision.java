package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.time.Instant;

@Entity
@Table(name = "revisions", catalog = "auth_history")
@RevisionEntity
public class AuditRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    private Long id;

    @RevisionTimestamp
    @Column(name = "revised_at", nullable = false)
    private Instant revisedAt;

    protected AuditRevision() {
    }

    public Long id() {
        return id;
    }

    public Instant revisedAt() {
        return revisedAt;
    }
}
