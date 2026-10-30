package com.apg.promociones.apg_api.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        properties.setToken("test-token");
        filter = new SimulatedAuthFilter(properties);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SimulatedRequestContext.clear();
    }

    @Test
    void acceptsBearerTokenAndUserId() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        request.addHeader(SimulatedAuthFilter.USER_ID_HEADER, "10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
        assertNull(SimulatedRequestContext.get());
    }

    @Test
    void acceptsBasicPasswordAsTestToken() throws Exception {
        MockHttpServletRequest request = apiRequest();
        String basic = Base64.getEncoder().encodeToString("cliente:test-token".getBytes(StandardCharsets.UTF_8));
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic);
        request.addHeader(SimulatedAuthFilter.USER_ID_HEADER, "7");
        request.addHeader(SimulatedAuthFilter.RESTAURANT_ID_HEADER, "3");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsMissingToken() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(SimulatedAuthFilter.USER_ID_HEADER, "10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("The test token is missing"));
    }

    @Test
    void rejectsInvalidToken() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer other-token");
        request.addHeader(SimulatedAuthFilter.USER_ID_HEADER, "10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("invalid"));
    }

    @Test
    void rejectsMissingUserId() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("X-User-Id"));
    }

    private static MockHttpServletRequest apiRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/coupons");
        request.setMethod("POST");
        return request;
    }
}
