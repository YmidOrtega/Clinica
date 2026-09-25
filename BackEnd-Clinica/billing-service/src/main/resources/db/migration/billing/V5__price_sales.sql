ALTER TABLE sales
    ADD COLUMN edited_at       DATETIME(6)   NULL AFTER active_lines,
    ADD COLUMN contract_uuid   CHAR(36)      NULL AFTER edited_at,
    ADD COLUMN contract_number VARCHAR(40)   NULL AFTER contract_uuid,
    ADD COLUMN payer_uuid      CHAR(36)      NULL AFTER contract_number,
    ADD COLUMN lines_total     DECIMAL(14,2) NULL AFTER payer_uuid,
    ADD COLUMN packages_total  DECIMAL(14,2) NULL AFTER lines_total,
    ADD COLUMN total           DECIMAL(14,2) NULL AFTER packages_total,
    ADD CONSTRAINT chk_sales_settlement
        CHECK ((status = 'CONFIRMED' AND lines_total IS NOT NULL AND packages_total IS NOT NULL AND total IS NOT NULL
                   AND total = lines_total + packages_total AND lines_total >= 0 AND packages_total >= 0)
            OR (status = 'DRAFT' AND lines_total IS NULL AND packages_total IS NULL AND total IS NULL
                   AND contract_uuid IS NULL)
            OR status = 'CANCELLED');

ALTER TABLE sale_lines
    ADD COLUMN manual_unit_price    DECIMAL(14,2) NULL AFTER authorization_number,
    ADD COLUMN manual_price_reason  VARCHAR(500)  NULL AFTER manual_unit_price,
    ADD COLUMN price_origin         VARCHAR(20)   NULL AFTER manual_price_reason,
    ADD COLUMN unit_price           DECIMAL(14,2) NULL AFTER price_origin,
    ADD COLUMN line_total           DECIMAL(14,2) NULL AFTER unit_price,
    ADD COLUMN price_reference_uuid CHAR(36)      NULL AFTER line_total,
    ADD COLUMN price_reference_code VARCHAR(40)   NULL AFTER price_reference_uuid,
    ADD CONSTRAINT chk_sale_lines_manual_price
        CHECK ((manual_unit_price IS NULL AND manual_price_reason IS NULL)
            OR (manual_unit_price > 0 AND CHAR_LENGTH(TRIM(manual_price_reason)) >= 1)),
    ADD CONSTRAINT chk_sale_lines_price_origin
        CHECK (price_origin IS NULL OR price_origin IN ('PACKAGE', 'CONTRACT_EXCEPTION', 'TARIFF_MANUAL',
                                                        'CAPITATION', 'GLOBAL_BUDGET', 'MANUAL')),
    ADD CONSTRAINT chk_sale_lines_price
        CHECK ((price_origin IS NULL AND unit_price IS NULL AND line_total IS NULL)
            OR (price_origin IS NOT NULL AND unit_price >= 0 AND line_total = unit_price * quantity)),
    ADD CONSTRAINT chk_sale_lines_covered_in_zero
        CHECK (price_origin IS NULL OR price_origin NOT IN ('PACKAGE', 'CAPITATION', 'GLOBAL_BUDGET')
            OR line_total = 0),
    ADD CONSTRAINT chk_sale_lines_manual_origin
        CHECK (price_origin IS NULL OR price_origin <> 'MANUAL' OR unit_price = manual_unit_price);

CREATE TABLE sale_packages (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    sale_id      BIGINT        NOT NULL,
    package_uuid CHAR(36)      NOT NULL,
    code         VARCHAR(40)   NOT NULL,
    name         VARCHAR(200)  NOT NULL,
    price        DECIMAL(14,2) NOT NULL,

    CONSTRAINT pk_sale_packages PRIMARY KEY (id),
    CONSTRAINT uk_sale_packages_package UNIQUE (sale_id, package_uuid),
    CONSTRAINT fk_sale_packages_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT chk_sale_packages_price CHECK (price >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE billing_history.sales_aud
    ADD COLUMN edited_at       DATETIME(6)   NULL,
    ADD COLUMN contract_uuid   CHAR(36)      NULL,
    ADD COLUMN contract_number VARCHAR(40)   NULL,
    ADD COLUMN payer_uuid      CHAR(36)      NULL,
    ADD COLUMN lines_total     DECIMAL(14,2) NULL,
    ADD COLUMN packages_total  DECIMAL(14,2) NULL,
    ADD COLUMN total           DECIMAL(14,2) NULL;

ALTER TABLE billing_history.sale_lines_aud
    ADD COLUMN manual_unit_price    DECIMAL(14,2) NULL,
    ADD COLUMN manual_price_reason  VARCHAR(500)  NULL,
    ADD COLUMN price_origin         VARCHAR(20)   NULL,
    ADD COLUMN unit_price           DECIMAL(14,2) NULL,
    ADD COLUMN line_total           DECIMAL(14,2) NULL,
    ADD COLUMN price_reference_uuid CHAR(36)      NULL,
    ADD COLUMN price_reference_code VARCHAR(40)   NULL;

CREATE TABLE billing_history.sale_packages_aud (
    id           BIGINT        NOT NULL,
    rev          BIGINT        NOT NULL,
    revtype      TINYINT       NOT NULL,
    sale_id      BIGINT        NULL,
    package_uuid CHAR(36)      NULL,
    code         VARCHAR(40)   NULL,
    name         VARCHAR(200)  NULL,
    price        DECIMAL(14,2) NULL,

    CONSTRAINT pk_sale_packages_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_sale_packages_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
