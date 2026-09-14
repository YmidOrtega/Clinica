ALTER DATABASE `${flyway:defaultSchema}` DEFAULT ENCRYPTION = 'Y';
ALTER DATABASE clinical_ledger DEFAULT ENCRYPTION = 'Y';
ALTER DATABASE clinical_workspace DEFAULT ENCRYPTION = 'Y';

ALTER TABLE `${flyway:defaultSchema}`.patient_references ENCRYPTION = 'Y';
ALTER TABLE clinical_ledger.encounters ENCRYPTION = 'Y';
ALTER TABLE clinical_ledger.encounter_closures ENCRYPTION = 'Y';
ALTER TABLE clinical_ledger.chain_links ENCRYPTION = 'Y';
ALTER TABLE clinical_ledger.notes ENCRYPTION = 'Y';
ALTER TABLE clinical_ledger.note_voids ENCRYPTION = 'Y';
ALTER TABLE clinical_workspace.note_drafts ENCRYPTION = 'Y';
