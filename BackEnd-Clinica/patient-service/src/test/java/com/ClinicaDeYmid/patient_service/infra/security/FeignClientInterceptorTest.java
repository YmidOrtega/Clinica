package com.ClinicaDeYmid.patient_service.infra.security;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para FeignClientInterceptor.
 * Verifica que el token JWT se propague correctamente en las peticiones Feign.
 */
@ExtendWith(MockitoExtension.class)
class FeignClientInterceptorTest {

    @InjectMocks
    private FeignClientInterceptor feignClientInterceptor;

    @Mock
    private RequestTemplate requestTemplate;

    private static final String VALID_TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXV1aWQiLCJlbWFpbCI6InRlc3RAZXhhbXBsZS5jb20ifQ.test";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @BeforeEach
    void setUp() {
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void apply_ShouldAddAuthorizationHeader_WhenTokenExistsInRequest() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + VALID_TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(requestTemplate.url()).thenReturn("http://clients-service/api/v1/test");

        // Act
        feignClientInterceptor.apply(requestTemplate);

        // Assert
        verify(requestTemplate).header(eq(AUTHORIZATION_HEADER), eq(BEARER_PREFIX + VALID_TOKEN));
    }

    @Test
    void apply_ShouldNotAddHeader_WhenNoTokenInRequest() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(requestTemplate.url()).thenReturn("http://clients-service/api/v1/test");

        // Act
        feignClientInterceptor.apply(requestTemplate);

        // Assert
        verify(requestTemplate, never()).header(eq(AUTHORIZATION_HEADER), anyString());
    }

    @Test
    void apply_ShouldNotAddHeader_WhenTokenHasInvalidFormat() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, "InvalidFormat " + VALID_TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(requestTemplate.url()).thenReturn("http://clients-service/api/v1/test");

        // Act
        feignClientInterceptor.apply(requestTemplate);

        // Assert
        verify(requestTemplate, never()).header(eq(AUTHORIZATION_HEADER), anyString());
    }

    @Test
    void apply_ShouldNotAddHeader_WhenRequestContextIsNull() {
        // Arrange
        RequestContextHolder.resetRequestAttributes();
        when(requestTemplate.url()).thenReturn("http://clients-service/api/v1/test");

        // Act
        feignClientInterceptor.apply(requestTemplate);

        // Assert
        verify(requestTemplate, never()).header(eq(AUTHORIZATION_HEADER), anyString());
    }

    @Test
    void apply_ShouldHandleEmptyAuthorizationHeader() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, "");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(requestTemplate.url()).thenReturn("http://clients-service/api/v1/test");

        // Act
        feignClientInterceptor.apply(requestTemplate);

        // Assert
        verify(requestTemplate, never()).header(eq(AUTHORIZATION_HEADER), anyString());
    }

    @Test
    void apply_ShouldHandleNullAuthorizationHeader() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(requestTemplate.url()).thenReturn("http://clients-service/api/v1/test");

        // Act
        feignClientInterceptor.apply(requestTemplate);

        // Assert
        verify(requestTemplate, never()).header(eq(AUTHORIZATION_HEADER), anyString());
    }

    @Test
    void apply_ShouldExtractTokenCorrectly_WhenBearerPrefixPresent() {
        // Arrange
        String tokenWithBearer = BEARER_PREFIX + VALID_TOKEN;
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, tokenWithBearer);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(requestTemplate.url()).thenReturn("http://clients-service/api/v1/test");

        // Act
        feignClientInterceptor.apply(requestTemplate);

        // Assert
        verify(requestTemplate).header(eq(AUTHORIZATION_HEADER), eq(tokenWithBearer));
    }
}
