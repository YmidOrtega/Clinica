ALTER TABLE billing_outbox.outbox_events
    DROP CONSTRAINT chk_outbox_events_aggregatetype,
    DROP CONSTRAINT chk_outbox_events_type,
    ADD CONSTRAINT chk_outbox_events_aggregatetype
        CHECK (aggregatetype IN ('billing.invoices', 'billing.filing-deadlines', 'billing.claim-objections')),
    ADD CONSTRAINT chk_outbox_events_type CHECK (type IN (
        'InvoiceIssued', 'InvoiceSigned', 'InvoiceAcceptedByDian', 'InvoiceRejectedByDian', 'InvoiceCredited',
        'InvoiceVoided', 'CreditNoteAcceptedByDian', 'InvoiceRipsValidated', 'InvoiceFiled', 'InvoiceFilingCorrected',
        'InvoiceObjectionRegistered', 'InvoiceObjectionAnswered', 'InvoiceObjectionDecided',
        'FilingDeadlineApproaching', 'FilingDeadlineMissed', 'ObjectionResponseDueSoon', 'ObjectionResponseMissed'));
