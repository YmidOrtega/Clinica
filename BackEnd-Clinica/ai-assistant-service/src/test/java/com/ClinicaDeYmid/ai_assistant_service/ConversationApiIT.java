package com.ClinicaDeYmid.ai_assistant_service;

import com.ClinicaDeYmid.ai_assistant_service.support.JwtTestTokens;
import com.ClinicaDeYmid.ai_assistant_service.support.PostgresTestContainer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class ConversationApiIT {

    private static final String CONVERSATIONS = "/api/v1/assistant/conversations";

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestTokens.register(registry);
    }

    @Test
    void theBillerOpensReadsAndClosesTheirConversation() throws Exception {
        UUID biller = UUID.randomUUID();
        String conversation = JsonPath.read(as(JwtTestTokens.bearerAs("BILLING", biller), post(CONVERSATIONS)
                        .content("{\"title\":\"  Facturas rechazadas   de septiembre \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.title").value("Facturas rechazadas de septiembre"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString(), "$.uuid");

        as(JwtTestTokens.bearerAs("BILLING", biller), get(CONVERSATIONS))
                .andExpect(jsonPath("$.content[0].uuid").value(conversation))
                .andExpect(jsonPath("$.totalElements").value(1));
        as(JwtTestTokens.bearerAs("BILLING", biller), get(CONVERSATIONS + "/" + conversation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").isEmpty());

        as(JwtTestTokens.bearerAs("BILLING", biller), post(CONVERSATIONS + "/" + conversation + "/closure"))
                .andExpect(status().isPreconditionRequired());
        as(JwtTestTokens.bearerAs("BILLING", biller), post(CONVERSATIONS + "/" + conversation + "/closure")
                .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").exists());
        as(JwtTestTokens.bearerAs("BILLING", biller), post(CONVERSATIONS + "/" + conversation + "/closure")
                .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONVERSATION_CLOSED"));
    }

    @Test
    void nobodyReadsAnotherUsersConversation() throws Exception {
        String conversation = JsonPath.read(as(JwtTestTokens.bearerAs("BILLING", UUID.randomUUID()),
                post(CONVERSATIONS).content("{\"title\":\"Glosas de Nueva EPS\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");
        UUID colleague = UUID.randomUUID();

        as(JwtTestTokens.bearerAs("ACCOUNTS_RECEIVABLE", colleague), get(CONVERSATIONS + "/" + conversation))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        as(JwtTestTokens.bearerAs("ACCOUNTS_RECEIVABLE", colleague), get(CONVERSATIONS))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void onlyInvoicingAndReceivablesUseTheAssistant() throws Exception {
        as(JwtTestTokens.bearer("RECEPTIONIST"), get(CONVERSATIONS)).andExpect(status().isForbidden());
        mockMvc.perform(get(CONVERSATIONS)).andExpect(status().isUnauthorized());
        as(JwtTestTokens.bearer("BILLING"), post(CONVERSATIONS).content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions as(String bearer, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }
}
