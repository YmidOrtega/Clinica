CREATE TABLE graphic_representations (
    id                     CHAR(36)     NOT NULL,
    electronic_document_id BIGINT       NOT NULL,
    document_number        VARCHAR(24)  NOT NULL,
    issued_by              CHAR(36)     NOT NULL,
    issued_by_role         VARCHAR(40)  NOT NULL,
    issued_at              DATETIME(6)  NOT NULL,
    document_sha256        VARCHAR(64)  NOT NULL,
    key_id                 VARCHAR(64)  NOT NULL,
    seal                   VARCHAR(512) NOT NULL,

    CONSTRAINT pk_graphic_representations PRIMARY KEY (id),
    CONSTRAINT fk_graphic_representations_document
        FOREIGN KEY (electronic_document_id) REFERENCES electronic_documents (id),
    CONSTRAINT uk_graphic_representations_fingerprint UNIQUE (document_number, document_sha256),
    CONSTRAINT chk_graphic_representations_sha256 CHECK (REGEXP_LIKE(document_sha256, '^[0-9a-f]{64}$', 'c')),
    INDEX idx_graphic_representations_document (electronic_document_id, issued_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
