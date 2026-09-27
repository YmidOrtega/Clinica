package com.ClinicaDeYmid.billing_service.domain;

public record ObjectionCode(String code, Kind kind, String concept, String groupCode, boolean applicable,
                            String description) {

    public enum Kind {
        DEVOLUTION,
        GLOSS,
        RESPONSE
    }
}
