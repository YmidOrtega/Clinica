ALTER TABLE tariff_items
    ADD COLUMN surgical_basis DECIMAL(9, 2) NULL AFTER value,
    ADD CONSTRAINT chk_tariff_items_surgical_basis CHECK (surgical_basis IS NULL OR surgical_basis > 0);

ALTER TABLE tariff_manual_versions
    ADD COLUMN surgical_item_count     INT      NOT NULL DEFAULT 0 AFTER item_count,
    ADD COLUMN surgical_rules_checksum VARCHAR(64) NULL AFTER surgical_item_count,
    ADD CONSTRAINT chk_tariff_manual_versions_surgical_items
        CHECK (surgical_item_count >= 0 AND surgical_item_count <= item_count),
    ADD CONSTRAINT chk_tariff_manual_versions_surgical_rules
        CHECK (surgical_rules_checksum IS NULL OR REGEXP_LIKE(surgical_rules_checksum, '^[0-9a-f]{64}$', 'c')),
    ADD CONSTRAINT chk_tariff_manual_versions_published_with_rules
        CHECK (status = 'DRAFT' OR surgical_item_count = 0 OR surgical_rules_checksum IS NOT NULL);

ALTER TABLE contracting_history.tariff_manual_versions_aud
    ADD COLUMN surgical_item_count     INT      NULL,
    ADD COLUMN surgical_rules_checksum VARCHAR(64) NULL;

CREATE TABLE surgical_rule_sets (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)    NOT NULL,
    manual_version_id BIGINT      NOT NULL,
    basis             VARCHAR(20) NOT NULL,
    checksum          VARCHAR(64) NOT NULL,
    registered_at     DATETIME(6) NOT NULL,
    registered_by     VARCHAR(36) NULL,

    CONSTRAINT pk_surgical_rule_sets PRIMARY KEY (id),
    CONSTRAINT uk_surgical_rule_sets_uuid UNIQUE (uuid),
    CONSTRAINT uk_surgical_rule_sets_version UNIQUE (manual_version_id),
    CONSTRAINT fk_surgical_rule_sets_version FOREIGN KEY (manual_version_id) REFERENCES tariff_manual_versions (id),

    CONSTRAINT chk_surgical_rule_sets_basis CHECK (basis IN ('UVR', 'SURGICAL_GROUP')),
    CONSTRAINT chk_surgical_rule_sets_checksum CHECK (REGEXP_LIKE(checksum, '^[0-9a-f]{64}$', 'c'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE surgical_component_rules (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    rule_set_id             BIGINT         NOT NULL,
    component               VARCHAR(20)    NOT NULL,
    mode                    VARCHAR(20)    NOT NULL,
    rate                    DECIMAL(15, 4) NULL,
    ranges                  JSON           NULL,
    minimum_basis           DECIMAL(9, 2)  NULL,
    same_route_percent      DECIMAL(5, 2)  NOT NULL,
    different_route_percent DECIMAL(5, 2)  NOT NULL,

    CONSTRAINT pk_surgical_component_rules PRIMARY KEY (id),
    CONSTRAINT uk_surgical_component_rules_component UNIQUE (rule_set_id, component),
    CONSTRAINT fk_surgical_component_rules_set FOREIGN KEY (rule_set_id) REFERENCES surgical_rule_sets (id),

    CONSTRAINT chk_surgical_component_rules_component
        CHECK (component IN ('SURGEON', 'ANESTHESIOLOGIST', 'ASSISTANT', 'OPERATING_ROOM', 'MATERIALS')),
    CONSTRAINT chk_surgical_component_rules_mode
        CHECK ((mode = 'PER_UNIT' AND rate IS NOT NULL AND rate >= 0 AND ranges IS NULL)
            OR (mode = 'BY_RANGE' AND rate IS NULL AND ranges IS NOT NULL AND JSON_LENGTH(ranges) > 0)),
    CONSTRAINT chk_surgical_component_rules_minimum CHECK (minimum_basis IS NULL OR minimum_basis >= 0),
    CONSTRAINT chk_surgical_component_rules_percents
        CHECK (same_route_percent BETWEEN 0 AND 100 AND different_route_percent BETWEEN 0 AND 100)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
