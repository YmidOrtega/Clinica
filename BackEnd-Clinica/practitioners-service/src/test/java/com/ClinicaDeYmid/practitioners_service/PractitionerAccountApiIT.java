package com.ClinicaDeYmid.practitioners_service;

import com.ClinicaDeYmid.commons.security.StaffAccessRegistry;
import com.ClinicaDeYmid.practitioners_service.support.JwtTestTokens;
import com.ClinicaDeYmid.practitioners_service.support.MySqlTestContainer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class PractitionerAccountApiIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffAccessRegistry registry;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @BeforeEach
    void copyOfTheUsersTopicIsUpToDate() {
        ReflectionTestUtils.setField(registry, "caughtUp", true);
    }

    @Test
    void linksAnExistingAccountAndShowsItsState() throws Exception {
        UUID user = knownUser("ACTIVE");
        String uuid = register();

        as("HUMAN_RESOURCES", put("/api/v1/practitioners/" + uuid + "/account")
                .header(HttpHeaders.IF_MATCH, "\"0\"").content("{\"userUuid\":\"" + user + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.linked").value(true))
                .andExpect(jsonPath("$.account.userUuid").value(user.toString()))
                .andExpect(jsonPath("$.account.state").value("ACTIVE"));

        as("HUMAN_RESOURCES", post("/api/v1/practitioners/search").content("{\"authUserUuid\":\"" + user + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uuid").value(uuid));
    }

    @Test
    void reflectsThatTheAccountIsNoLongerActiveWithoutTouchingThePractitioner() throws Exception {
        UUID user = knownUser("ACTIVE");
        String uuid = register();
        as("HUMAN_RESOURCES", put("/api/v1/practitioners/" + uuid + "/account")
                .header(HttpHeaders.IF_MATCH, "\"0\"").content("{\"userUuid\":\"" + user + "\"}"))
                .andExpect(status().isOk());

        registry.record(user, new StaffAccessRegistry.StaffAccess(2, "SUSPENDED", Instant.now()));

        as("HUMAN_RESOURCES", get("/api/v1/practitioners/" + uuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.state").value("INACTIVE"))
                .andExpect(jsonPath("$.status.code").value("ACTIVE"));
    }

    @Test
    void refusesAnAccountThatTheUsersTopicDoesNotKnow() throws Exception {
        String uuid = register();

        as("HUMAN_RESOURCES", put("/api/v1/practitioners/" + uuid + "/account")
                .header(HttpHeaders.IF_MATCH, "\"0\"").content("{\"userUuid\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AUTH_USER_NOT_FOUND"));
    }

    @Test
    void refusesToLinkTheSameAccountTwiceAndToUnlinkWhatIsNotLinked() throws Exception {
        UUID user = knownUser("ACTIVE");
        String first = register();
        String second = register();

        as("HUMAN_RESOURCES", put("/api/v1/practitioners/" + first + "/account")
                .header(HttpHeaders.IF_MATCH, "\"0\"").content("{\"userUuid\":\"" + user + "\"}"))
                .andExpect(status().isOk());

        as("HUMAN_RESOURCES", put("/api/v1/practitioners/" + second + "/account")
                .header(HttpHeaders.IF_MATCH, "\"0\"").content("{\"userUuid\":\"" + user + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_USER_ALREADY_LINKED"));

        as("HUMAN_RESOURCES", delete("/api/v1/practitioners/" + second + "/account")
                .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_LINKED"));

        as("HUMAN_RESOURCES", delete("/api/v1/practitioners/" + first + "/account")
                .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.linked").value(false));
    }

    @Test
    void refusesToLinkWhileTheCopyOfTheUsersTopicIsNotReady() throws Exception {
        UUID user = knownUser("ACTIVE");
        String uuid = register();
        ReflectionTestUtils.setField(registry, "caughtUp", false);

        as("HUMAN_RESOURCES", put("/api/v1/practitioners/" + uuid + "/account")
                .header(HttpHeaders.IF_MATCH, "\"0\"").content("{\"userUuid\":\"" + user + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DIRECTORY_UNAVAILABLE"));
    }

    private UUID knownUser(String status) {
        UUID user = UUID.randomUUID();
        registry.record(user, new StaffAccessRegistry.StaffAccess(1, status, Instant.now()));
        return user;
    }

    private String register() throws Exception {
        int sequence = SEQUENCE.incrementAndGet();
        String created = as("HUMAN_RESOURCES", post("/api/v1/practitioners")
                .content("{\"document\":{\"type\":\"CEDULA_DE_CIUDADANIA\",\"number\":\"20" + (5000000 + sequence) + "\"},"
                        + "\"firstNames\":\"Carlos\",\"lastNames\":\"Pérez Díaz\","
                        + "\"registration\":{\"number\":\"RC-" + (20000 + sequence) + "\"},"
                        + "\"contact\":{\"email\":\"cuenta" + sequence + "@clinica.local\",\"mobile\":\"3005551122\"},"
                        + "\"relationship\":\"CONTRACTOR\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.uuid");
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role))
                .contentType(MediaType.APPLICATION_JSON));
    }
}
