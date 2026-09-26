package com.ClinicaDeYmid.contracting_service;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
import com.ClinicaDeYmid.contracting_service.support.JwtTestTokens;
import com.ClinicaDeYmid.contracting_service.support.MySqlTestContainer;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class ContractingEventsIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(700);
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
    void everyContractChangeLeavesItsFullStateInTheOutbox() throws Exception {
        String tariffVersion = publishedTariffVersion();
        String contract = draftContract();

        stepUp(put("/api/v1/contracts/" + contract + "/tariff-terms"), 0L,
                "{\"tariffVersionUuid\":\"" + tariffVersion + "\",\"factor\":1.2}").andExpect(status().isOk());
        stepUp(post("/api/v1/contracts/" + contract + "/activation"), 1L, "{}").andExpect(status().isOk());
        stepUp(post("/api/v1/contracts/" + contract + "/tariff-exceptions"), null,
                "{\"cupsCode\":\"890201\",\"agreedPrice\":45000,\"reason\":\"Negociación puntual de consulta\","
                        + "\"validFrom\":\"2026-01-01\"}").andExpect(status().isOk());

        List<Map<String, Object>> events = eventsOf(contract);

        assertThat(events).extracting(row -> row.get("type"))
                .containsExactly("ContractDrafted", "ContractTariffTermsAgreed", "ContractActivated",
                        "ContractTariffExceptionRegistered");
        assertThat(events).allSatisfy(row -> assertThat(row.get("aggregatetype")).isEqualTo("contracting.contracts"));

        JsonNode last = payloadOf(events.get(events.size() - 1));
        assertThat(last.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(last.get("tariffTerms").get("factor").decimalValue()).isEqualByComparingTo("1.2000");
        assertThat(last.get("tariffTerms").get("unit").asText()).isEqualTo("COP");
        assertThat(last.get("tariffExceptions")).hasSize(1);
        assertThat(last.get("tariffExceptions").get(0).get("cupsCode").asText()).isEqualTo("890201");
        assertThat(last.get("payer").get("nit").asText()).isNotBlank();
    }

    @Test
    void aRevokedExceptionDisappearsFromTheState() throws Exception {
        String contract = activeContract();
        String exception = JsonPath.read(stepUp(post("/api/v1/contracts/" + contract + "/tariff-exceptions"), null,
                        "{\"cupsCode\":\"890201\",\"agreedPrice\":45000,\"reason\":\"Negociación puntual de consulta\","
                                + "\"validFrom\":\"2026-01-01\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.uuid");

        stepUp(post("/api/v1/contracts/tariff-exceptions/" + exception + "/revocation"), null,
                "{\"from\":\"2026-06-01\",\"reason\":\"Se renegoció el precio con el pagador\"}")
                .andExpect(status().isOk());

        JsonNode state = payloadOf(eventsOf(contract).getLast());
        assertThat(state.get("type").asText()).isEqualTo("ContractTariffExceptionRevoked");
        assertThat(state.get("tariffExceptions")).isEmpty();
    }

    @Test
    void publishingATariffVersionAnnouncesItAndRetiresThePrevious() throws Exception {
        String manual = registerManual();
        String first = draftVersion(manual, "2025", "2025-01-01");
        loadTariffs(first);
        stepUp(post("/api/v1/tariff-manuals/versions/" + first + "/activation"), 1L, "{}").andExpect(status().isOk());

        String second = draftVersion(manual, "2026", "2026-01-01");
        loadTariffs(second);
        stepUp(post("/api/v1/tariff-manuals/versions/" + second + "/activation"), 1L, "{}").andExpect(status().isOk());

        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT type, payload FROM contracting_outbox.outbox_events WHERE aggregatetype = 'contracting.tariffs' "
                        + "AND aggregateid = ? ORDER BY created_at, id", manual);

        assertThat(events).extracting(row -> row.get("type"))
                .containsExactly("TariffVersionPublished", "TariffVersionRetired", "TariffVersionPublished");
        JsonNode retired = payloadOf(events.get(1));
        assertThat(retired.get("status").asText()).isEqualTo("RETIRED");
        assertThat(retired.get("label").asText()).isEqualTo("2025");
        assertThat(payloadOf(events.get(2)).get("itemCount").asInt()).isEqualTo(2);
        assertThat(payloadOf(events.get(2)).get("sourceChecksum").asText()).hasSize(64);
    }

    @Test
    void everyPublishedEventFollowsItsContract() throws Exception {
        String contract = activeContract();
        stepUp(post("/api/v1/contracts/" + contract + "/packages"), null,
                "{\"code\":\"PAQ-" + SEQUENCE.incrementAndGet() + "\",\"name\":\"Paquete de atención\",\"price\":150000,"
                        + "\"includedCodes\":[\"890201\",\"903841\"],\"validFrom\":\"2026-01-01\"}")
                .andExpect(status().isOk());
        stepUp(put("/api/v1/contracts/" + contract + "/rips-registration"), 2L,
                "{\"coveragePlan\":\"UPC_CONTRIBUTORY\",\"cucon\":\"" + "ab".repeat(32) + "\"}")
                .andExpect(status().isOk());
        assertThat(eventsOf(contract)).extracting(row -> row.get("type")).contains("ContractRipsRegistered");
        assertThat(payloadOf(eventsOf(contract).getLast()).get("coveragePlan").asText()).isEqualTo("UPC_CONTRIBUTORY");

        JsonSchema contractsSchema = schema("contracting.contracts.v1.schema.json");
        JsonSchema tariffsSchema = schema("contracting.tariffs.v1.schema.json");

        for (Map<String, Object> event : jdbc.queryForList(
                "SELECT aggregatetype, payload FROM contracting_outbox.outbox_events")) {
            JsonSchema schema = "contracting.contracts".equals(event.get("aggregatetype")) ? contractsSchema : tariffsSchema;
            assertThat(schema.validate(payloadOf(event))).isEmpty();
        }
    }

    private List<Map<String, Object>> eventsOf(String contractUuid) {
        return jdbc.queryForList("SELECT type, aggregatetype, payload FROM contracting_outbox.outbox_events "
                + "WHERE aggregatetype = 'contracting.contracts' AND aggregateid = ? ORDER BY created_at, id", contractUuid);
    }

    private static JsonNode payloadOf(Map<String, Object> row) throws Exception {
        return JSON.readTree(String.valueOf(row.get("payload")));
    }

    private static JsonSchema schema(String file) throws Exception {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(Files.readString(Path.of("events", file)));
    }

    private String activeContract() throws Exception {
        String contract = draftContract();
        stepUp(put("/api/v1/contracts/" + contract + "/tariff-terms"), 0L,
                "{\"tariffVersionUuid\":\"" + publishedTariffVersion() + "\",\"factor\":1.0}").andExpect(status().isOk());
        stepUp(post("/api/v1/contracts/" + contract + "/activation"), 1L, "{}").andExpect(status().isOk());
        return contract;
    }

    private String draftContract() throws Exception {
        String nit = String.format("5%08d", SEQUENCE.incrementAndGet());
        String payer = JsonPath.read(as("CONTRACTING", post("/api/v1/payers").content(
                        "{\"identity\":{\"socialReason\":\"Pagador de eventos\",\"nit\":\"" + nit + "\",\"type\":\"EPS\"},"
                                + "\"contact\":{\"address\":\"Calle 1 # 2-3\",\"phone\":\"6011112222\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");

        return JsonPath.read(as("CONTRACTING", post("/api/v1/contracts").content(
                        "{\"payerUuid\":\"" + payer + "\",\"number\":\"EVT-" + SEQUENCE.incrementAndGet()
                                + "\",\"name\":\"Contrato de eventos\",\"modality\":\"EVENT\","
                                + "\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");
    }

    private String registerManual() throws Exception {
        return JsonPath.read(as("CONTRACTING", post("/api/v1/tariff-manuals").content(
                        "{\"code\":\"EVT_" + SEQUENCE.incrementAndGet() + "\",\"name\":\"Manual de eventos\",\"unit\":\"COP\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");
    }

    private String draftVersion(String manual, String label, String validFrom) throws Exception {
        return JsonPath.read(as("CONTRACTING", post("/api/v1/tariff-manuals/" + manual + "/versions").content(
                        "{\"label\":\"" + label + "\",\"unitValue\":1,\"validFrom\":\"" + validFrom + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");
    }

    private void loadTariffs(String version) throws Exception {
        as("CONTRACTING", post("/api/v1/tariff-manuals/versions/" + version + "/items").content(
                        "{\"items\":[{\"cupsCode\":\"890201\",\"description\":\"Consulta\",\"value\":40000},"
                                + "{\"cupsCode\":\"903841\",\"description\":\"Hemograma\",\"value\":18000}]}"))
                .andExpect(status().isOk());
    }

    private String publishedTariffVersion() throws Exception {
        String manual = registerManual();
        String version = draftVersion(manual, "2026", "2026-01-01");
        loadTariffs(version);
        stepUp(post("/api/v1/tariff-manuals/versions/" + version + "/activation"), 1L, "{}").andExpect(status().isOk());
        return version;
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role)));
    }

    private ResultActions stepUp(MockHttpServletRequestBuilder request, Long version, String body) throws Exception {
        UUID contracting = UUID.fromString(JwtTestTokens.USERS.get("CONTRACTING"));
        MockHttpServletRequestBuilder prepared = request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("CONTRACTING", contracting).bearer())
                .content(body);
        if (version != null) {
            prepared = prepared.header(HttpHeaders.IF_MATCH, "\"" + version + "\"");
        }
        return mockMvc.perform(prepared);
    }
}
