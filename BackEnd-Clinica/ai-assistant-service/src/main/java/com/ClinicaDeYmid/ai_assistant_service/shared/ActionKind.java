package com.ClinicaDeYmid.ai_assistant_service.shared;

public enum ActionKind {

    SIGN("Firmar la factura que quedó sin firma"),
    SEND_TO_DIAN("Enviar o reenviar la factura a la DIAN"),
    VALIDATE_RIPS("Enviar el RIPS y la factura al Ministerio para obtener el CUV");

    private final String label;

    ActionKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
