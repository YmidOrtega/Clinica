package com.ClinicaDeYmid.clinical_history_service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AccessAuditContract {

    public static final Path SCHEMA_PATH = Path.of("events/clinical.access-audit.v1.schema.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchema SCHEMA = load();

    private AccessAuditContract() {
    }

    public static List<String> violations(String json) {
        try {
            return SCHEMA.validate(MAPPER.readTree(json)).stream().map(ValidationMessage::getMessage).toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static JsonSchema load() {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
        try (InputStream schema = Files.newInputStream(SCHEMA_PATH)) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schema, config);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
