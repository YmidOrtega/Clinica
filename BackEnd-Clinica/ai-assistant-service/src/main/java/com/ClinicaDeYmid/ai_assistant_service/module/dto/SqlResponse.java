package com.ClinicaDeYmid.ai_assistant_service.module.dto;

import com.ClinicaDeYmid.ai_assistant_service.module.enums.SqlOperation;

public record SqlResponse(SqlOperation operation, String sql) {

}
