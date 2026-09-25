ALTER TABLE sales
    ADD COLUMN surgery_performed_on DATE NULL AFTER type,
    ADD COLUMN surgical_team        JSON NULL AFTER surgery_performed_on,
    DROP CHECK chk_sales_type,
    ADD CONSTRAINT chk_sales_type CHECK (type IN ('NON_SURGICAL', 'SURGICAL')),
    ADD CONSTRAINT chk_sales_surgery
        CHECK ((type = 'SURGICAL' AND surgery_performed_on IS NOT NULL AND surgical_team IS NOT NULL)
            OR (type = 'NON_SURGICAL' AND surgery_performed_on IS NULL AND surgical_team IS NULL));

ALTER TABLE sale_lines
    ADD COLUMN kind                VARCHAR(20)   NOT NULL DEFAULT 'SERVICE' AFTER quantity,
    ADD COLUMN route               VARCHAR(30)   NULL AFTER kind,
    ADD COLUMN surgical_order      INT           NULL AFTER price_reference_code,
    ADD COLUMN principal_procedure BOOLEAN       NULL AFTER surgical_order,
    ADD COLUMN same_route          BOOLEAN       NULL AFTER principal_procedure,
    ADD COLUMN surgical_basis      DECIMAL(9, 2) NULL AFTER same_route,
    ADD COLUMN components          JSON          NULL AFTER surgical_basis,
    ADD CONSTRAINT chk_sale_lines_kind
        CHECK ((kind = 'SERVICE' AND route IS NULL)
            OR (kind = 'PROCEDURE' AND route IS NOT NULL AND quantity = 1)),
    DROP CHECK chk_sale_lines_price_origin,
    ADD CONSTRAINT chk_sale_lines_price_origin
        CHECK (price_origin IS NULL OR price_origin IN ('PACKAGE', 'CONTRACT_EXCEPTION', 'TARIFF_MANUAL',
                                                        'SURGICAL_LIQUIDATION', 'CAPITATION', 'GLOBAL_BUDGET',
                                                        'MANUAL')),
    ADD CONSTRAINT chk_sale_lines_liquidation
        CHECK ((price_origin = 'SURGICAL_LIQUIDATION' AND kind = 'PROCEDURE' AND surgical_order >= 1
                   AND principal_procedure IS NOT NULL AND same_route IS NOT NULL AND components IS NOT NULL)
            OR ((price_origin IS NULL OR price_origin <> 'SURGICAL_LIQUIDATION') AND surgical_order IS NULL
                   AND components IS NULL));

ALTER TABLE sale_lines ALTER COLUMN kind DROP DEFAULT;

ALTER TABLE billing_history.sales_aud
    ADD COLUMN surgery_performed_on DATE NULL,
    ADD COLUMN surgical_team        JSON NULL;

ALTER TABLE billing_history.sale_lines_aud
    ADD COLUMN kind                VARCHAR(20)   NULL,
    ADD COLUMN route               VARCHAR(30)   NULL,
    ADD COLUMN surgical_order      INT           NULL,
    ADD COLUMN principal_procedure BOOLEAN       NULL,
    ADD COLUMN same_route          BOOLEAN       NULL,
    ADD COLUMN surgical_basis      DECIMAL(9, 2) NULL,
    ADD COLUMN components          JSON          NULL;
