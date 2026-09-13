package com.ClinicaDeYmid.commons.web;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {

    private static final String SENSITIVE_VALUE = "1098765432";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @ParameterizedTest
    @CsvSource({
            "INVALID_INPUT, 400",
            "NOT_FOUND, 404",
            "CONFLICT, 409",
            "RULE_VIOLATION, 422",
            "FORBIDDEN, 403",
            "PRECONDITION_FAILED, 412",
            "PRECONDITION_REQUIRED, 428",
            "DEPENDENCY_UNAVAILABLE, 503"
    })
    void mapsEveryDomainCategoryToItsHttpStatus(ErrorCategory category, int expectedStatus) throws Exception {
        mockMvc.perform(get("/domain/{category}", category))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("SAMPLE_RULE"))
                .andExpect(jsonPath("$.type").value("urn:clinica:error:sample-rule"))
                .andExpect(jsonPath("$.detail").value("Mensaje público"))
                .andExpect(jsonPath("$.instance", startsWith("urn:uuid:")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void includesTraceIdFromLoggingContext() throws Exception {
        MDC.put(ProblemDetails.TRACE_ID, "4bf92f3577b34da6a3ce929d0e0e4736");

        mockMvc.perform(get("/domain/NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("4bf92f3577b34da6a3ce929d0e0e4736"))
                .andExpect(jsonPath("$.instance").value("urn:clinica:trace:4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    @Test
    void omitsTraceIdWhenTracingIsNotActive() throws Exception {
        mockMvc.perform(get("/domain/NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void reportsInvalidBodyFieldsWithoutEchoingRejectedValues() throws Exception {
        mockMvc.perform(post("/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"document\":\"" + SENSITIVE_VALUE + SENSITIVE_VALUE + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'document')]").exists())
                .andExpect(content().string(not(containsString(SENSITIVE_VALUE))));
    }

    @Test
    void reportsInvalidRequestParameters() throws Exception {
        mockMvc.perform(get("/search").param("q", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("q"));
    }

    @Test
    void rejectsMalformedPathValuesWithoutEchoingThem() throws Exception {
        mockMvc.perform(get("/items/{id}", SENSITIVE_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(content().string(not(containsString(SENSITIVE_VALUE))));
    }

    @Test
    void rejectsUnreadableJson() throws Exception {
        mockMvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void rejectsUnsupportedMethods() throws Exception {
        mockMvc.perform(put("/body"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void hidesDatabaseDetailsOnIntegrityViolations() throws Exception {
        mockMvc.perform(get("/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(content().string(not(containsString("Duplicate entry"))))
                .andExpect(content().string(not(containsString(SENSITIVE_VALUE))));
    }

    @Test
    void reportsConcurrentModifications() throws Exception {
        mockMvc.perform(get("/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void hidesUnexpectedErrorDetails() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado"))
                .andExpect(content().string(not(containsString("NullPointerException"))))
                .andExpect(content().string(not(containsString("secret-internal-state"))));
    }

    @RestController
    static class TestController {

        @GetMapping("/domain/{category}")
        void domain(@PathVariable ErrorCategory category) {
            throw new SampleException(category);
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody SampleRequest request) {
        }

        @GetMapping("/search")
        void search(@RequestParam @Size(min = 2) String q) {
        }

        @GetMapping("/items/{id}")
        void item(@PathVariable UUID id) {
        }

        @GetMapping("/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("Duplicate entry '" + SENSITIVE_VALUE + "' for key 'uk_document'");
        }

        @GetMapping("/stale")
        void stale() {
            throw new OptimisticLockingFailureException("Row was updated by another transaction");
        }

        @GetMapping("/boom")
        void boom() {
            throw new NullPointerException("secret-internal-state");
        }
    }

    record SampleRequest(@NotBlank String name, @Size(max = 12) String document) {
    }

    static final class SampleException extends DomainException {
        SampleException(ErrorCategory category) {
            super(category, "SAMPLE_RULE", "Mensaje público");
        }
    }
}
