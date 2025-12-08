package com.ClinicaDeYmid.ai_assistant_service.config;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

public class CustomH2Dialect extends H2Dialect {

    public CustomH2Dialect() {
        super();
    }

    @Override
    public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry);
        // Register the JDBC descriptor for NAMED_ENUM (6001) to be handled as VARCHAR
        typeContributions.getTypeConfiguration().getJdbcTypeRegistry()
            .addDescriptor(SqlTypes.NAMED_ENUM, VarcharJdbcType.INSTANCE);
    }

    @Override
    protected String columnType(int sqlTypeCode) {
        if (sqlTypeCode == SqlTypes.NAMED_ENUM) {
            return "varchar";
        }
        return super.columnType(sqlTypeCode);
    }
}
