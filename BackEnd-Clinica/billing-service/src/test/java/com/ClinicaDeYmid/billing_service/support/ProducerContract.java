package com.ClinicaDeYmid.billing_service.support;

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

    public static final ProducerContract ADMISSION_EVENTS =
            new ProducerContract(Path.of("../admissions-service/events/admissions.events.v1.schema.json"));
    public static final ProducerContract CLINICAL_ENCOUNTERS =
            new ProducerContract(Path.of("../clinical-history-service/events/clinical.encounters.v1.schema.json"));

    public static final ProducerContract FILING_DEADLINES =
            new ProducerContract(Path.of("events/billing.filing-deadlines.v1.schema.json"));

    public static final ProducerContract INVOICES =
            new ProducerContract(Path.of("events/billing.invoices.v1.schema.json"));
    public static final ProducerContract CLAIM_OBJECTIONS =
            new ProducerContract(Path.of("events/billing.claim-objections.v1.schema.json"));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonSchema schema;

    private ProducerContract(Path file) {
        this.schema = load(file);
    }

    public List<String> breaches(String json) {
        try {
            return schema.validate(MAPPER.readTree(json)).stream().map(ValidationMessage::getMessage).toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static JsonSchema load(Path file) {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
        try (InputStream source = Files.newInputStream(file)) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(source, config);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
