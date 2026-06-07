-- =====================================================
-- 1. TABLA SALE_ORDER (Cabecera de la venta)
-- =====================================================

CREATE TABLE sale_order (
    id                   BIGINT          NOT NULL AUTO_INCREMENT,
    attention_id         BIGINT          NOT NULL,
    patient_id           VARCHAR(20)     NOT NULL,
    health_provider_nit  VARCHAR(20)     NOT NULL,
    contract_id          BIGINT          DEFAULT NULL,
    doctor_id            BIGINT          DEFAULT NULL,
    status               ENUM(
                             'DRAFT',
                             'CONFIRMED',
                             'INVOICED',
                             'CANCELLED'
                         )               NOT NULL DEFAULT 'DRAFT',
    subtotal             DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    tax_amount           DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    total_amount         DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    copayment_amount     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    net_amount           DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    notes                VARCHAR(500)    DEFAULT NULL,
    created_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by           VARCHAR(100)    DEFAULT NULL,
    updated_by           VARCHAR(100)    DEFAULT NULL,

    PRIMARY KEY (id),

    INDEX idx_sale_order_attention_id (attention_id),
    INDEX idx_sale_order_patient_id (patient_id),
    INDEX idx_sale_order_health_provider_nit (health_provider_nit),
    INDEX idx_sale_order_contract_id (contract_id),
    INDEX idx_sale_order_status (status),
    INDEX idx_sale_order_created_at (created_at),
    -- Búsqueda combinada: ventas activas por atención y estado
    INDEX idx_sale_order_attention_status (attention_id, status),

    CONSTRAINT chk_sale_order_patient_id_not_empty
        CHECK (TRIM(patient_id) != ''),
    CONSTRAINT chk_sale_order_nit_format
        CHECK (health_provider_nit REGEXP '^[0-9]{6,12}(-[0-9])?$'),
    CONSTRAINT chk_sale_order_subtotal_positive
        CHECK (subtotal >= 0),
    CONSTRAINT chk_sale_order_total_positive
        CHECK (total_amount >= 0),
    CONSTRAINT chk_sale_order_copayment_positive
        CHECK (copayment_amount >= 0),
    CONSTRAINT chk_sale_order_net_positive
        CHECK (net_amount >= 0),
    CONSTRAINT chk_sale_order_status_values
        CHECK (status IN ('DRAFT', 'CONFIRMED', 'INVOICED', 'CANCELLED'))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE sale_order
COMMENT = 'Cabecera de la venta/cuenta de cobro generada a partir de una atención';

-- =====================================================
-- 2. TABLA SALE_ORDER_ITEM (Ítems de la venta)
-- =====================================================

CREATE TABLE sale_order_item (
    id                   BIGINT          NOT NULL AUTO_INCREMENT,
    sale_order_id        BIGINT          NOT NULL,
    portfolio_id         BIGINT          DEFAULT NULL,
    code_cups            VARCHAR(50)     DEFAULT NULL,
    code_clinic          VARCHAR(50)     DEFAULT NULL,
    description          VARCHAR(300)    NOT NULL,
    item_type            ENUM(
                             'EXAM',
                             'PROCEDURE',
                             'SUPPLY',
                             'HONORARIUM',
                             'ROOM',
                             'OTHER'
                         )               NOT NULL,
    quantity             INT             NOT NULL DEFAULT 1,
    unit_price           DECIMAL(15,2)   NOT NULL,
    discount_percentage  DECIMAL(5,2)    DEFAULT NULL,
    tax_rate             DECIMAL(5,2)    DEFAULT NULL,
    subtotal             DECIMAL(15,2)   NOT NULL,
    authorized           BOOLEAN         NOT NULL DEFAULT FALSE,
    authorization_id     BIGINT          DEFAULT NULL,

    PRIMARY KEY (id),

    INDEX idx_sale_order_item_sale_order_id (sale_order_id),
    INDEX idx_sale_order_item_portfolio_id (portfolio_id),
    INDEX idx_sale_order_item_type (item_type),
    INDEX idx_sale_order_item_code_cups (code_cups),
    INDEX idx_sale_order_item_authorized (authorized),

    CONSTRAINT fk_sale_order_item_sale_order
        FOREIGN KEY (sale_order_id) REFERENCES sale_order(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT chk_sale_order_item_description_not_empty
        CHECK (TRIM(description) != ''),
    CONSTRAINT chk_sale_order_item_quantity_positive
        CHECK (quantity > 0),
    CONSTRAINT chk_sale_order_item_unit_price_positive
        CHECK (unit_price >= 0),
    CONSTRAINT chk_sale_order_item_subtotal_positive
        CHECK (subtotal >= 0),
    CONSTRAINT chk_sale_order_item_discount_range
        CHECK (discount_percentage IS NULL OR (discount_percentage >= 0 AND discount_percentage <= 100)),
    CONSTRAINT chk_sale_order_item_tax_range
        CHECK (tax_rate IS NULL OR (tax_rate >= 0 AND tax_rate <= 100)),
    CONSTRAINT chk_sale_order_item_type_values
        CHECK (item_type IN ('EXAM', 'PROCEDURE', 'SUPPLY', 'HONORARIUM', 'ROOM', 'OTHER'))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE sale_order_item
COMMENT = 'Líneas de la venta: exámenes, insumos, honorarios, habitación u otros servicios';
