CREATE TABLE stay_segments (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    account_id BIGINT      NOT NULL,
    bed_uuid   CHAR(36)    NOT NULL,
    stay_type  VARCHAR(30) NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at   DATETIME(6) NULL,

    CONSTRAINT pk_stay_segments PRIMARY KEY (id),
    CONSTRAINT fk_stay_segments_account FOREIGN KEY (account_id) REFERENCES episode_accounts (id),

    CONSTRAINT chk_stay_segments_type
        CHECK (stay_type IS NULL OR stay_type IN ('OBSERVATION', 'GENERAL_WARD', 'SHARED_ROOM', 'PRIVATE_ROOM',
                                                  'INTERMEDIATE_CARE', 'ICU_ADULT', 'ICU_PEDIATRIC', 'ICU_NEONATAL')),
    CONSTRAINT chk_stay_segments_period CHECK (ended_at IS NULL OR ended_at >= started_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_stay_segments_account ON stay_segments (account_id, started_at);

CREATE TABLE stay_charges (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    version             BIGINT       NOT NULL,
    stay_type           VARCHAR(30)  NOT NULL,
    portfolio_item_uuid CHAR(36)     NOT NULL,
    cups_code           VARCHAR(8)   NOT NULL,
    clinic_code         VARCHAR(20)  NULL,
    description         VARCHAR(300) NOT NULL,
    category            VARCHAR(40)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    updated_by          VARCHAR(36)  NULL,

    CONSTRAINT pk_stay_charges PRIMARY KEY (id),
    CONSTRAINT uk_stay_charges_type UNIQUE (stay_type),

    CONSTRAINT chk_stay_charges_type
        CHECK (stay_type IN ('OBSERVATION', 'GENERAL_WARD', 'SHARED_ROOM', 'PRIVATE_ROOM', 'INTERMEDIATE_CARE',
                             'ICU_ADULT', 'ICU_PEDIATRIC', 'ICU_NEONATAL')),
    CONSTRAINT chk_stay_charges_cups CHECK (REGEXP_LIKE(cups_code, '^[0-9]{6,8}$', 'c'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE sales
    ADD COLUMN origin    VARCHAR(20) NOT NULL DEFAULT 'MANUAL' AFTER type,
    ADD COLUMN stay_slot BIGINT GENERATED ALWAYS AS (CASE WHEN origin = 'STAY' THEN account_id END) STORED,
    ADD CONSTRAINT uk_sales_single_stay UNIQUE (stay_slot),
    ADD CONSTRAINT chk_sales_origin CHECK (origin IN ('MANUAL', 'STAY') AND (origin = 'MANUAL' OR type = 'NON_SURGICAL'));

ALTER TABLE sales ALTER COLUMN origin DROP DEFAULT;

ALTER TABLE sale_lines
    DROP CHECK chk_sale_lines_origin,
    ADD CONSTRAINT chk_sale_lines_origin
        CHECK ((origin IN ('MANUAL', 'STAY') AND authorization_uuid IS NULL AND authorization_number IS NULL)
            OR (origin = 'AUTHORIZED' AND authorization_uuid IS NOT NULL AND authorization_number IS NOT NULL));

CREATE TABLE billing_history.stay_segments_aud (
    id         BIGINT      NOT NULL,
    rev        BIGINT      NOT NULL,
    revtype    TINYINT     NOT NULL,
    account_id BIGINT      NULL,
    bed_uuid   CHAR(36)    NULL,
    stay_type  VARCHAR(30) NULL,
    started_at DATETIME(6) NULL,
    ended_at   DATETIME(6) NULL,

    CONSTRAINT pk_stay_segments_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_stay_segments_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE billing_history.stay_charges_aud (
    id                  BIGINT       NOT NULL,
    rev                 BIGINT       NOT NULL,
    revtype             TINYINT      NOT NULL,
    stay_type           VARCHAR(30)  NULL,
    portfolio_item_uuid CHAR(36)     NULL,
    cups_code           VARCHAR(8)   NULL,
    clinic_code         VARCHAR(20)  NULL,
    description         VARCHAR(300) NULL,
    category            VARCHAR(40)  NULL,
    created_at          DATETIME(6)  NULL,
    updated_at          DATETIME(6)  NULL,
    updated_by          VARCHAR(36)  NULL,

    CONSTRAINT pk_stay_charges_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_stay_charges_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE billing_history.sales_aud
    ADD COLUMN origin VARCHAR(20) NULL;
