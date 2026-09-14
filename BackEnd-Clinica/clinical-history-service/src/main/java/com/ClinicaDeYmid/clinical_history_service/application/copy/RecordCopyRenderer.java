package com.ClinicaDeYmid.clinical_history_service.application.copy;

public interface RecordCopyRenderer {

    byte[] render(RecordCopyContent content);
}
