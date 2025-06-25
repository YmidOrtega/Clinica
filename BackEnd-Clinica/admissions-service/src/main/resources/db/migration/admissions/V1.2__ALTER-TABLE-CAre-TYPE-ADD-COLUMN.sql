ALTER TABLE care_types
ADD COLUMN service_type_id BIGINT NOT NULL,
ADD CONSTRAINT fk_care_types_service_type FOREIGN KEY (service_type_id) REFERENCES service_types(id);