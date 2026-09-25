ALTER TABLE admissions.rooms
    ADD COLUMN stay_type VARCHAR(30) NOT NULL DEFAULT 'GENERAL_WARD';

ALTER TABLE admissions.rooms ALTER COLUMN stay_type DROP DEFAULT;

ALTER TABLE admissions.rooms
    ADD CONSTRAINT chk_rooms_stay_type
        CHECK (stay_type IN ('OBSERVATION', 'GENERAL_WARD', 'SHARED_ROOM', 'PRIVATE_ROOM', 'INTERMEDIATE_CARE',
                             'ICU_ADULT', 'ICU_PEDIATRIC', 'ICU_NEONATAL'));

ALTER TABLE admissions_history.rooms_aud
    ADD COLUMN stay_type VARCHAR(30);
