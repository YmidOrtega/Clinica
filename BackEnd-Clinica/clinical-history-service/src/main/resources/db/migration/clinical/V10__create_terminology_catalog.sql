CREATE TABLE terminology_releases (
    id            CHAR(36)     NOT NULL,
    code_system   VARCHAR(20)  NOT NULL,
    version       VARCHAR(40)  NOT NULL,
    checksum      CHAR(64)     NOT NULL,
    source_file   VARCHAR(255) NOT NULL,
    concept_count INT          NOT NULL,
    imported_at   DATETIME(6)  NOT NULL,
    imported_by   CHAR(36)     NOT NULL,

    CONSTRAINT pk_terminology_releases PRIMARY KEY (id),
    CONSTRAINT uk_terminology_releases_checksum UNIQUE (code_system, checksum),
    CONSTRAINT uk_terminology_releases_version UNIQUE (code_system, version),
    CONSTRAINT chk_terminology_releases_system CHECK (code_system IN ('CIE10')),
    CONSTRAINT chk_terminology_releases_checksum CHECK (REGEXP_LIKE(checksum, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT chk_terminology_releases_count CHECK (concept_count > 0)
) ENGINE = InnoDB ENCRYPTION = 'Y';

CREATE TABLE terminology_concepts (
    release_id       CHAR(36)     NOT NULL,
    code             VARCHAR(10)  NOT NULL,
    display          VARCHAR(300) NOT NULL,
    category_code    VARCHAR(10)  NOT NULL,
    category_display VARCHAR(300) NULL,
    chapter          INT          NOT NULL,
    chapter_display  VARCHAR(200) NOT NULL,

    CONSTRAINT pk_terminology_concepts PRIMARY KEY (release_id, code),
    CONSTRAINT fk_terminology_concepts_release FOREIGN KEY (release_id) REFERENCES terminology_releases (id),
    CONSTRAINT chk_terminology_concepts_chapter CHECK (chapter BETWEEN 1 AND 22)
) ENGINE = InnoDB ENCRYPTION = 'Y';

CREATE TABLE terminology_activations (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    code_system  VARCHAR(20) NOT NULL,
    release_id   CHAR(36)    NOT NULL,
    activated_at DATETIME(6) NOT NULL,
    activated_by CHAR(36)    NOT NULL,

    CONSTRAINT pk_terminology_activations PRIMARY KEY (id),
    CONSTRAINT fk_terminology_activations_release FOREIGN KEY (release_id) REFERENCES terminology_releases (id)
) ENGINE = InnoDB ENCRYPTION = 'Y';

CREATE INDEX idx_terminology_activations_system ON terminology_activations (code_system, id);
