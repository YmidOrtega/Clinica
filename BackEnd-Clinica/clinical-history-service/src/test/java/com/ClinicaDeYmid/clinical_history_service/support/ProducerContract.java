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

public final class ProducerContract {

    public static final Path PATIENT_EVENTS_SCHEMA = Path.of("../patient-service/events/patient.events.v1.schema.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchema SCHEMA = load();

    private ProducerContract() {
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
        try (InputStream schema = Files.newInputStream(PATIENT_EVENTS_SCHEMA)) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schema, config);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
