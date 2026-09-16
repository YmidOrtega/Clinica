CREATE TABLE portfolio_items (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)     NOT NULL,
    version           BIGINT       NOT NULL,
    cups_code         VARCHAR(8)   NOT NULL,
    clinic_code       VARCHAR(20)  NOT NULL,
    name              VARCHAR(200) NOT NULL,
    category          VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    status_reason     VARCHAR(500) NULL,
    status_changed_at DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    created_by        VARCHAR(36)  NULL,
    updated_at        DATETIME(6)  NOT NULL,
    updated_by        VARCHAR(36)  NULL,

    CONSTRAINT pk_portfolio_items PRIMARY KEY (id),
    CONSTRAINT uk_portfolio_items_uuid UNIQUE (uuid),
    CONSTRAINT uk_portfolio_items_clinic_code UNIQUE (clinic_code),

    CONSTRAINT chk_portfolio_items_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_portfolio_items_version CHECK (version >= 0),
    CONSTRAINT chk_portfolio_items_cups_code CHECK (REGEXP_LIKE(cups_code, '^[0-9]{6,8}$', 'c')),
    CONSTRAINT chk_portfolio_items_clinic_code CHECK (REGEXP_LIKE(clinic_code, '^[A-Z0-9][A-Z0-9.-]{1,19}$', 'c')),
    CONSTRAINT chk_portfolio_items_name CHECK (CHAR_LENGTH(TRIM(name)) >= 3),
    CONSTRAINT chk_portfolio_items_category
        CHECK (category IN ('CONSULTATION', 'PROCEDURE', 'SURGERY', 'LABORATORY', 'IMAGING', 'HOSPITALIZATION',
                            'SUPPLY', 'MEDICATION', 'TRANSPORT', 'OTHER')),
    CONSTRAINT chk_portfolio_items_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_portfolio_items_status_details
        CHECK ((status = 'ACTIVE' AND status_reason IS NULL AND status_changed_at IS NULL)
            OR (status = 'INACTIVE' AND CHAR_LENGTH(status_reason) >= 10 AND status_changed_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_portfolio_items_cups_code ON portfolio_items (cups_code);
CREATE INDEX idx_portfolio_items_name ON portfolio_items (name);

CREATE TABLE contracting_history.portfolio_items_aud (
    id                BIGINT       NOT NULL,
    rev               BIGINT       NOT NULL,
    revtype           TINYINT      NOT NULL,
    uuid              CHAR(36)     NULL,
    cups_code         VARCHAR(8)   NULL,
    clinic_code       VARCHAR(20)  NULL,
    name              VARCHAR(200) NULL,
    category          VARCHAR(20)  NULL,
    status            VARCHAR(20)  NULL,
    status_reason     VARCHAR(500) NULL,
    status_changed_at DATETIME(6)  NULL,
    created_at        DATETIME(6)  NULL,
    created_by        VARCHAR(36)  NULL,
    updated_at        DATETIME(6)  NULL,
    updated_by        VARCHAR(36)  NULL,

    CONSTRAINT pk_portfolio_items_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_portfolio_items_aud_revision FOREIGN KEY (rev) REFERENCES contracting_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
