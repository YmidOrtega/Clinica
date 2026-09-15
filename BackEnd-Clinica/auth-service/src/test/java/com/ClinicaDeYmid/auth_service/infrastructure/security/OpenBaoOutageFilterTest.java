package com.ClinicaDeYmid.auth_service.infrastructure.security;

import com.ClinicaDeYmid.commons.openbao.transit.OpenBaoUnavailableException;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtEncodingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenBaoOutageFilterTest {

    private final OpenBaoOutageFilter filter = new OpenBaoOutageFilter();

    @Test
    void anOpenBaoOutageWhileSigningTokensIsATemporaryUnavailability() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("POST", "/oauth2/token"), response, (request, ignored) -> {
            throw new JwtEncodingException("Could not sign", new OpenBaoUnavailableException("OpenBao did not answer", null));
        });

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getContentAsString()).contains("\"error\": \"temporarily_unavailable\"");
    }

    @Test
    void otherFailuresKeepPropagating() {
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("POST", "/oauth2/token"), new MockHttpServletResponse(),
                (request, response) -> {
                    throw new ServletException("broken");
                })).isInstanceOf(ServletException.class);
    }
}
