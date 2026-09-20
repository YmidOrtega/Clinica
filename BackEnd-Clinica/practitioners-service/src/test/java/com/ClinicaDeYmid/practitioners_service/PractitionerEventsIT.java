package com.ClinicaDeYmid.practitioners_service;

import com.ClinicaDeYmid.practitioners_service.support.JwtTestTokens;
import com.ClinicaDeYmid.practitioners_service.support.MySqlTestContainer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class PractitionerEventsIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(400);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void everyChangeLeavesTheFullStateInTheOutbox() throws Exception {
        String specialtyCode = nextCode();
        registerSpecialty(specialtyCode);
        String uuid = register();

        as("HUMAN_RESOURCES", put("/api/v1/practitioners/" + uuid + "/specialties")
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .content("{\"specialties\":[{\"specialtyCode\":\"" + specialtyCode + "\",\"principal\":true}]}"))
                .andExpect(status().isOk());
        as("HUMAN_RESOURCES", post("/api/v1/practitioners/" + uuid + "/suspension")
                .header(HttpHeaders.IF_MATCH, "\"1\"").content("{\"reason\":\"Investigación disciplinaria\"}"))
                .andExpect(status().isOk());

        List<Map<String, Object>> events = eventsOf(uuid);

        assertThat(events).extracting(row -> row.get("type"))
                .containsExactly("PractitionerRegistered", "PractitionerSpecialtiesAssigned", "PractitionerSuspended");
        assertThat(events).allSatisfy(row -> assertThat(row.get("aggregatetype")).isEqualTo("practitioners"));

        JsonNode last = payloadOf(events.getLast());
        assertThat(last.get("status").get("code").asText()).isEqualTo("SUSPENDED");
        assertThat(last.get("status").get("reason").asText()).isEqualTo("Investigación disciplinaria");
        assertThat(last.get("registration").get("number").asText()).isNotBlank();
        assertThat(last.get("specialties")).hasSize(1);
        assertThat(last.get("specialties").get(0).get("principal").asBoolean()).isTrue();
        assertThat(last.get("version").asLong()).isEqualTo(2);
    }

    @Test
    void theFeesNeverLeaveTheService() throws Exception {
        String uuid = register();
        as("HUMAN_RESOURCES", post("/api/v1/practitioners/" + uuid + "/fee-agreements")
                .content("{\"basis\":\"HOURLY\",\"amount\":85000.00,\"validFrom\":\"2026-01-01\"}"))
                .andExpect(status().isCreated());

        assertThat(eventsOf(uuid)).extracting(row -> row.get("type")).containsExactly("PractitionerRegistered");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practitioners_outbox.outbox_events "
                + "WHERE payload LIKE '%85000%'", Integer.class)).isZero();
    }

    @Test
    void everyPublishedEventFollowsItsContract() throws Exception {
        String uuid = register();
        as("HUMAN_RESOURCES", put("/api/v1/practitioners/" + uuid + "/contact")
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .content("{\"email\":\"nuevo" + SEQUENCE.incrementAndGet() + "@clinica.local\",\"mobile\":\"3004445566\"}"))
                .andExpect(status().isOk());
        as("HUMAN_RESOURCES", post("/api/v1/practitioners/" + uuid + "/retirement")
                .header(HttpHeaders.IF_MATCH, "\"1\"").content("{\"reason\":\"Terminó su contrato\"}"))
                .andExpect(status().isOk());

        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(Files.readString(Path.of("events", "practitioners.v1.schema.json")));

        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT payload FROM practitioners_outbox.outbox_events");
        assertThat(events).isNotEmpty();
        for (Map<String, Object> event : events) {
            assertThat(schema.validate(payloadOf(event))).isEmpty();
        }
    }

    @Test
    void aFailedChangeLeavesNoEventBehind() throws Exception {
        String uuid = register();
        int before = eventsOf(uuid).size();

        as("HUMAN_RESOURCES", put("/api/v1/practitioners/" + uuid + "/specialties")
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .content("{\"specialties\":[{\"specialtyCode\":\"NO-EXISTE\",\"principal\":true}]}"))
                .andExpect(status().isNotFound());

        assertThat(eventsOf(uuid)).hasSize(before);
        as("HUMAN_RESOURCES", get("/api/v1/practitioners/" + uuid))
                .andExpect(status().isOk());
    }

    private List<Map<String, Object>> eventsOf(String practitionerUuid) {
        return jdbc.queryForList("SELECT type, aggregatetype, payload FROM practitioners_outbox.outbox_events "
                + "WHERE aggregateid = ? ORDER BY created_at, id", practitionerUuid);
    }

    private static JsonNode payloadOf(Map<String, Object> row) throws Exception {
        return JSON.readTree(String.valueOf(row.get("payload")));
    }

    private void registerSpecialty(String code) throws Exception {
        as("HUMAN_RESOURCES", post("/api/v1/specialties").content(
                "{\"code\":\"" + code + "\",\"name\":\"Medicina interna\"}"))
                .andExpect(status().isCreated());
    }

    private String register() throws Exception {
        int sequence = SEQUENCE.incrementAndGet();
        String created = as("HUMAN_RESOURCES", post("/api/v1/practitioners")
                .content("{\"document\":{\"type\":\"CEDULA_DE_CIUDADANIA\",\"number\":\"40" + (7000000 + sequence) + "\"},"
                        + "\"firstNames\":\"Julián\",\"lastNames\":\"Mesa Ortiz\","
                        + "\"registration\":{\"number\":\"RE-" + (40000 + sequence) + "\"},"
                        + "\"contact\":{\"email\":\"evento" + sequence + "@clinica.local\",\"mobile\":\"3002223344\"},"
                        + "\"relationship\":\"STAFF\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.uuid");
    }

    private static String nextCode() {
        return "EVT" + SEQUENCE.incrementAndGet() + "W";
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role))
                .contentType(MediaType.APPLICATION_JSON));
    }
}
