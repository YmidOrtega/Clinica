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

    public static final Path ENCOUNTERS_SCHEMA_PATH = Path.of("events/clinical.encounters.v1.schema.json");

    public static final Path SCHEMA_PATH = Path.of("events/clinical.access-audit.v1.schema.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchema SCHEMA = load(SCHEMA_PATH);
    private static final JsonSchema ENCOUNTERS_SCHEMA = load(ENCOUNTERS_SCHEMA_PATH);

    private AccessAuditContract() {
    }

    public static List<String> violations(String json) {
        return validate(SCHEMA, json);
    }

    public static List<String> encounterEventViolations(String json) {
        return validate(ENCOUNTERS_SCHEMA, json);
    }

    private static List<String> validate(JsonSchema schema, String json) {
        try {
            return schema.validate(MAPPER.readTree(json)).stream().map(ValidationMessage::getMessage).toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static JsonSchema load(Path path) {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
        try (InputStream schema = Files.newInputStream(path)) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schema, config);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
