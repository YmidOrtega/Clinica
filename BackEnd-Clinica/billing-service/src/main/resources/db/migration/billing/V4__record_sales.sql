CREATE TABLE sales (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)     NOT NULL,
    version           BIGINT       NOT NULL,
    account_id        BIGINT       NOT NULL,
    sequence          INT          NOT NULL,
    number            VARCHAR(20)  NOT NULL,
    type              VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    status_reason     VARCHAR(500) NULL,
    status_changed_at DATETIME(6)  NULL,
    active_lines      INT          NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    created_by        VARCHAR(36)  NULL,
    updated_at        DATETIME(6)  NOT NULL,
    updated_by        VARCHAR(36)  NULL,

    CONSTRAINT pk_sales PRIMARY KEY (id),
    CONSTRAINT uk_sales_uuid UNIQUE (uuid),
    CONSTRAINT uk_sales_number UNIQUE (number),
    CONSTRAINT uk_sales_sequence UNIQUE (account_id, sequence),
    CONSTRAINT fk_sales_account FOREIGN KEY (account_id) REFERENCES episode_accounts (id),

    CONSTRAINT chk_sales_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_sales_version CHECK (version >= 0),
    CONSTRAINT chk_sales_sequence CHECK (sequence BETWEEN 1 AND 99),
    CONSTRAINT chk_sales_number CHECK (REGEXP_LIKE(number, '^ADM-[0-9]{4}-[0-9]{6}-V[0-9]{2}$', 'c')),
    CONSTRAINT chk_sales_type CHECK (type IN ('NON_SURGICAL')),
    CONSTRAINT chk_sales_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT chk_sales_active_lines CHECK (active_lines BETWEEN 0 AND 300),
    CONSTRAINT chk_sales_confirmed_with_lines CHECK (status <> 'CONFIRMED' OR active_lines > 0),
    CONSTRAINT chk_sales_status_details
        CHECK ((status = 'DRAFT' AND status_reason IS NULL AND status_changed_at IS NULL)
            OR (status = 'CONFIRMED' AND status_reason IS NULL AND status_changed_at IS NOT NULL)
            OR (status = 'CANCELLED' AND CHAR_LENGTH(TRIM(status_reason)) >= 1 AND status_changed_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sale_lines (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                 CHAR(36)     NOT NULL,
    sale_id              BIGINT       NOT NULL,
    position             INT          NOT NULL,
    portfolio_item_uuid  CHAR(36)     NOT NULL,
    cups_code            VARCHAR(8)   NOT NULL,
    clinic_code          VARCHAR(20)  NULL,
    description          VARCHAR(300) NOT NULL,
    category             VARCHAR(40)  NULL,
    quantity             INT          NOT NULL,
    service_date         DATE         NOT NULL,
    origin               VARCHAR(20)  NOT NULL,
    authorization_uuid   CHAR(36)     NULL,
    authorization_number VARCHAR(40)  NULL,
    removed_at           DATETIME(6)  NULL,
    removal_reason       VARCHAR(500) NULL,
    created_at           DATETIME(6)  NOT NULL,
    created_by           VARCHAR(36)  NULL,

    CONSTRAINT pk_sale_lines PRIMARY KEY (id),
    CONSTRAINT uk_sale_lines_uuid UNIQUE (uuid),
    CONSTRAINT uk_sale_lines_position UNIQUE (sale_id, position),
    CONSTRAINT fk_sale_lines_sale FOREIGN KEY (sale_id) REFERENCES sales (id),

    CONSTRAINT chk_sale_lines_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_sale_lines_position CHECK (position BETWEEN 1 AND 300),
    CONSTRAINT chk_sale_lines_cups CHECK (REGEXP_LIKE(cups_code, '^[0-9]{6,8}$', 'c')),
    CONSTRAINT chk_sale_lines_quantity CHECK (quantity BETWEEN 1 AND 999),
    CONSTRAINT chk_sale_lines_origin
        CHECK ((origin = 'MANUAL' AND authorization_uuid IS NULL AND authorization_number IS NULL)
            OR (origin = 'AUTHORIZED' AND authorization_uuid IS NOT NULL AND authorization_number IS NOT NULL)),
    CONSTRAINT chk_sale_lines_removal
        CHECK ((removed_at IS NULL AND removal_reason IS NULL)
            OR (removed_at IS NOT NULL AND CHAR_LENGTH(TRIM(removal_reason)) >= 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE billing_history.sales_aud (
    id                BIGINT       NOT NULL,
    rev               BIGINT       NOT NULL,
    revtype           TINYINT      NOT NULL,
    uuid              CHAR(36)     NULL,
    account_id        BIGINT       NULL,
    sequence          INT          NULL,
    number            VARCHAR(20)  NULL,
    type              VARCHAR(20)  NULL,
    status            VARCHAR(20)  NULL,
    status_reason     VARCHAR(500) NULL,
    status_changed_at DATETIME(6)  NULL,
    active_lines      INT          NULL,
    created_at        DATETIME(6)  NULL,
    created_by        VARCHAR(36)  NULL,
    updated_at        DATETIME(6)  NULL,
    updated_by        VARCHAR(36)  NULL,

    CONSTRAINT pk_sales_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_sales_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE billing_history.sale_lines_aud (
    id                   BIGINT       NOT NULL,
    rev                  BIGINT       NOT NULL,
    revtype              TINYINT      NOT NULL,
    uuid                 CHAR(36)     NULL,
    sale_id              BIGINT       NULL,
    position             INT          NULL,
    portfolio_item_uuid  CHAR(36)     NULL,
    cups_code            VARCHAR(8)   NULL,
    clinic_code          VARCHAR(20)  NULL,
    description          VARCHAR(300) NULL,
    category             VARCHAR(40)  NULL,
    quantity             INT          NULL,
    service_date         DATE         NULL,
    origin               VARCHAR(20)  NULL,
    authorization_uuid   CHAR(36)     NULL,
    authorization_number VARCHAR(40)  NULL,
    removed_at           DATETIME(6)  NULL,
    removal_reason       VARCHAR(500) NULL,
    created_at           DATETIME(6)  NULL,
    created_by           VARCHAR(36)  NULL,

    CONSTRAINT pk_sale_lines_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_sale_lines_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
