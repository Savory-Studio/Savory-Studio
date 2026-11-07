package com.apg.promociones.apg_api.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SimulatedAuthFilterTest {

    private SimulatedAuthFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        SimulatedAuthProperties properties = new SimulatedAuthProperties();
        properties.setEnabled(true);
        properties.setToken("test_token_123");
        filter = new SimulatedAuthFilter(properties);
        chain = mock(FilterChain.class);
    }

    @Test
    void rejectsRequestsWhenNoTokenIsConfigured() throws Exception {
        SimulatedAuthFilter unconfigured = new SimulatedAuthFilter(new SimulatedAuthProperties());
        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test_token_123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        unconfigured.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void acceptsValidBearerTokenWithoutExtraHeaders() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test_token_123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsBasicAuthenticationHeader() throws Exception {
        MockHttpServletRequest request = apiRequest();
        String basic = Base64.getEncoder().encodeToString("cliente:test_token_123".getBytes(StandardCharsets.UTF_8));
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("The Bearer token is missing"));
    }

    @Test
    void rejectsMissingBearerToken() throws Exception {
        MockHttpServletRequest request = apiRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("The Bearer token is missing"));
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("invalid"));
    }

    @Test
    void allowsPublicRegisterEndpointWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/register");
        request.setMethod("POST");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    private static MockHttpServletRequest apiRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/coupons");
        request.setMethod("POST");
        return request;
    }
}
