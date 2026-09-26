package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Entity
@Table(name = "document_files")
@EntityListeners(AuditingEntityListener.class)
public class DocumentFile {

    public enum Kind {
        UBL_UNSIGNED,
        UBL_SIGNED,
        DIAN_APPLICATION_RESPONSE,
        ATTACHED_DOCUMENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "electronic_document_id", nullable = false, updatable = false)
    private ElectronicDocument document;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 30)
    private Kind kind;

    @Lob
    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentFile() {
    }

    public static DocumentFile of(ElectronicDocument document, Kind kind, String content) {
        DocumentFile file = new DocumentFile();
        file.document = DomainRules.required(document, "document");
        file.kind = DomainRules.required(kind, "kind");
        file.content = DomainRules.required(content, "content");
        file.sha256 = sha256(content);
        return file;
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public Kind kind() {
        return kind;
    }

    public String content() {
        return content;
    }

    public String sha256() {
        return sha256;
    }
}
