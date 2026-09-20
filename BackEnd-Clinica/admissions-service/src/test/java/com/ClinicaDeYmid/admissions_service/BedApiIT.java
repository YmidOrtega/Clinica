package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.support.JwtTestTokens;
import com.ClinicaDeYmid.admissions_service.support.PostgresTestContainer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class BedApiIT {

    private static final String BASE = "/api/v1/admissions";
    private static final String CATALOGUE = BASE + "/catalogue";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void installsABedAndWalksItThroughItsWholeLifecycle() throws Exception {
        String bed = aBed();
        UUID occupant = UUID.randomUUID();

        as("NURSE", post(BASE + "/beds/" + bed + "/occupancy")
                .content("{\"occupantUuid\":\"" + occupant + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("OCCUPIED"))
                .andExpect(jsonPath("$.status.occupant").value(occupant.toString()));

        as("NURSE", post(BASE + "/beds/" + bed + "/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("CLEANING"));

        change("NURSE", post(BASE + "/beds/" + bed + "/cleaning-completion"), 2, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("AVAILABLE"));
    }

    @Test
    void refusesToOccupyABedThatIsAlreadyTaken() throws Exception {
        String bed = aBed();
        as("NURSE", post(BASE + "/beds/" + bed + "/occupancy")
                .content("{\"occupantUuid\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());

        as("NURSE", post(BASE + "/beds/" + bed + "/occupancy")
                .content("{\"occupantUuid\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void theDatabaseItselfRefusesTwoOverlappingStaysOnOneBed() throws Exception {
        String bed = aBed();
        long bedId = jdbc.queryForObject("SELECT id FROM admissions.beds WHERE uuid = ?::uuid", Long.class, bed);

        jdbc.update("INSERT INTO admissions.bed_stays (uuid, bed_id, occupant_uuid, started_at) "
                + "VALUES (gen_random_uuid(), ?, gen_random_uuid(), timestamp '2026-09-20 08:00:00')", bedId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO admissions.bed_stays (uuid, bed_id, occupant_uuid, started_at) "
                        + "VALUES (gen_random_uuid(), ?, gen_random_uuid(), timestamp '2026-09-20 09:00:00')", bedId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO admissions.bed_stays (uuid, bed_id, occupant_uuid, started_at, ended_at) "
                        + "VALUES (gen_random_uuid(), ?, gen_random_uuid(), "
                        + "timestamp '2026-09-19 08:00:00', timestamp '2026-09-20 10:00:00')", bedId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void acceptsStaysThatDoNotOverlapOnTheSameBed() throws Exception {
        String bed = aBed();
        long bedId = jdbc.queryForObject("SELECT id FROM admissions.beds WHERE uuid = ?::uuid", Long.class, bed);

        jdbc.update("INSERT INTO admissions.bed_stays (uuid, bed_id, occupant_uuid, started_at, ended_at) "
                + "VALUES (gen_random_uuid(), ?, gen_random_uuid(), "
                + "timestamp '2026-09-18 08:00:00', timestamp '2026-09-19 08:00:00')", bedId);
        jdbc.update("INSERT INTO admissions.bed_stays (uuid, bed_id, occupant_uuid, started_at) "
                + "VALUES (gen_random_uuid(), ?, gen_random_uuid(), timestamp '2026-09-19 08:00:00')", bedId);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM admissions.bed_stays WHERE bed_id = ?", Integer.class, bedId)).isEqualTo(2);
    }

    @Test
    void aBedInMaintenanceCannotBeOccupiedUntilItComesBack() throws Exception {
        String bed = aBed();

        change("ADMIN", post(BASE + "/beds/" + bed + "/maintenance"), 0, "{\"reason\":\"Cambio de colchón\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("MAINTENANCE"))
                .andExpect(jsonPath("$.status.reason").value("Cambio de colchón"));

        as("NURSE", post(BASE + "/beds/" + bed + "/occupancy")
                .content("{\"occupantUuid\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnprocessableEntity());

        change("ADMIN", post(BASE + "/beds/" + bed + "/return-to-service"), 1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("AVAILABLE"));
    }

    @Test
    void anOccupiedBedDisappearsFromTheAvailableList() throws Exception {
        String location = aLocation();
        String room = aRoom(location);
        String bed = aBedIn(room);

        as("NURSE", get(BASE + "/locations/" + location + "/available-beds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        as("NURSE", post(BASE + "/beds/" + bed + "/occupancy")
                .content("{\"occupantUuid\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());

        as("NURSE", get(BASE + "/locations/" + location + "/available-beds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "DOCTOR", "MEDICAL_RECORDS", "BILLING"})
    void onlyNursingMovesPatientsBetweenBeds(String role) throws Exception {
        String bed = aBed();

        as(role, post(BASE + "/beds/" + bed + "/occupancy")
                .content("{\"occupantUuid\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());

        as(role, get(BASE + "/beds/" + bed)).andExpect(status().isOk());
    }

    @Test
    void onlyAdministrationInstallsBeds() throws Exception {
        String room = aRoom(aLocation());

        as("NURSE", post(BASE + "/beds").content("{\"label\":\"X\",\"roomUuid\":\"" + room + "\"}"))
                .andExpect(status().isForbidden());
    }

    private String aBed() throws Exception {
        return aBedIn(aRoom(aLocation()));
    }

    private String aLocation() throws Exception {
        return uuidOf(as("ADMIN", post(CATALOGUE + "/locations")
                .content("{\"name\":\"" + unique("Piso") + "\"}")).andReturn().getResponse().getContentAsString());
    }

    private String aRoom(String location) throws Exception {
        return uuidOf(as("ADMIN", post(BASE + "/rooms")
                .content("{\"name\":\"" + unique("Hab") + "\",\"locationUuid\":\"" + location + "\"}"))
                .andReturn().getResponse().getContentAsString());
    }

    private String aBedIn(String room) throws Exception {
        return uuidOf(as("ADMIN", post(BASE + "/beds")
                .content("{\"label\":\"" + unique("Cama") + "\",\"roomUuid\":\"" + room + "\"}"))
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role)));
    }

    private ResultActions change(String role, MockHttpServletRequestBuilder request, long version, String body)
            throws Exception {
        MockHttpServletRequestBuilder prepared = request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role));
        return mockMvc.perform(body == null ? prepared.content("{}") : prepared.content(body));
    }

    private static String uuidOf(String json) {
        return JsonPath.read(json, "$.uuid");
    }

    private static String unique(String prefix) {
        return prefix + " " + SEQUENCE.incrementAndGet();
    }
}
