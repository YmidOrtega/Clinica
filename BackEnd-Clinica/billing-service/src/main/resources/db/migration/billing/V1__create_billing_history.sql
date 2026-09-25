CREATE DATABASE IF NOT EXISTS billing_history
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS billing_outbox
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE billing_history.revisions (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    revised_at DATETIME(6) NOT NULL,
    revised_by VARCHAR(36) NULL,

    CONSTRAINT pk_revisions PRIMARY KEY (id),
    CONSTRAINT chk_revisions_revised_by
        CHECK (revised_by IS NULL
            OR REGEXP_LIKE(revised_by, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
