package com.ClinicaDeYmid.billing_service;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ObjectionCodesIT extends IntegrationTest {

    private static final String CODES = "/api/v1/billing/objection-codes";

    @Test
    void listsTheWholeManualOfDevolutionsGlossesAndResponses() throws Exception {
        as("BILLING", get(CODES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(309))
                .andExpect(jsonPath("$[?(@.applicable == true)].code").value(org.hamcrest.Matchers.hasSize(200)));
        as("BILLING", get(CODES).param("kind", "RESPONSE"))
                .andExpect(jsonPath("$.length()").value(16))
                .andExpect(jsonPath("$[?(@.code == 'RE9702')].description").value(org.hamcrest.Matchers.contains(
                        "El prestador de servicios de salud o proveedor de tecnologías en salud informa a la entidad "
                                + "responsable de pago que la glosa ha sido aceptada al 100%")));
        as("BILLING", get(CODES).param("kind", "DEVOLUTION"))
                .andExpect(jsonPath("$[?(@.code == 'DE5601')].groupCode").value(org.hamcrest.Matchers.contains("DE56")));
        as("BILLING", get(CODES).param("kind", "GLOSS"))
                .andExpect(jsonPath("$[?(@.code == 'TA0201')].concept").value(org.hamcrest.Matchers.contains("TA")));
        as("RECEPTIONIST", get(CODES)).andExpect(status().isForbidden());
    }
}
