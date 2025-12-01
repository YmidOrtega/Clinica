package com.ClinicaDeYmid.ai_assistant_service.dto;

import com.ClinicaDeYmid.ai_assistant_service.enums.SqlOperation;

public record SqlResponse(SqlOperation operation, String sql) {

}
