package com.ClinicaDeYmid.auth_service.support;

import com.fasterxml.jackson.databind.JsonNode;
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

public final class EventContracts {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchema USERS = load(Path.of("events/auth.users.v1.schema.json"));
    private static final JsonSchema SECURITY_AUDIT = load(Path.of("events/auth.security-audit.v1.schema.json"));

    private EventContracts() {
    }

    public static List<String> userEventViolations(String json) {
        return validate(USERS, json);
    }

    public static List<String> securityAuditViolations(String json) {
        return validate(SECURITY_AUDIT, json);
    }

    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static List<String> validate(JsonSchema schema, String json) {
        return schema.validate(parse(json)).stream().map(ValidationMessage::getMessage).toList();
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
