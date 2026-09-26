ALTER TABLE document_files
    DROP CONSTRAINT chk_document_files_kind,
    ADD CONSTRAINT chk_document_files_kind
        CHECK (kind IN ('UBL_UNSIGNED', 'UBL_SIGNED', 'DIAN_APPLICATION_RESPONSE', 'ATTACHED_DOCUMENT'));
