package com.apg.promociones.apg_api.security;

import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.service.LoginServices.JwtService;
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

    private static final String JWT_SECRET = "MiClaveSuperSecretaYLoSuficientementeLargaParaHS25612345";

    private SimulatedAuthProperties properties;
    private JwtService jwtService;
    private SimulatedAuthFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        properties = new SimulatedAuthProperties();
        properties.setEnabled(true);
        properties.setToken("test_token_123");
        jwtService = new JwtService(JWT_SECRET, 86_400_000L);
        filter = new SimulatedAuthFilter(properties, jwtService);
        chain = mock(FilterChain.class);
    }

    @Test
    void rejectsRequestsWhenNoTokenOrJwtServiceIsConfigured() throws Exception {
        SimulatedAuthFilter unconfigured = new SimulatedAuthFilter(new SimulatedAuthProperties(), null);
        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test_token_123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        unconfigured.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void acceptsValidSignedJwtTokenOnProtectedRoutes_CP12() throws Exception {
        User user = User.builder()
                .id(12)
                .firstName("Carlos")
                .lastName("Pérez")
                .email("carlos.perez@example.com")
                .build();
        String jwt = jwtService.generateToken(user);

        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsTamperedJwtTokenOnProtectedRoutes_CP13() throws Exception {
        User user = User.builder()
                .id(12)
                .firstName("Carlos")
                .lastName("Pérez")
                .email("carlos.perez@example.com")
                .build();
        String jwt = jwtService.generateToken(user);
        String tamperedJwt = jwt.substring(0, jwt.length() - 4) + "XXXX";

        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedJwt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("invalid or expired"));
    }

    @Test
    void rejectsExpiredJwtTokenOnProtectedRoutes_CP14() throws Exception {
        JwtService expiredIssuer = new JwtService(JWT_SECRET, -5_000L);
        User user = User.builder()
                .id(12)
                .firstName("Carlos")
                .lastName("Pérez")
                .email("carlos.perez@example.com")
                .build();
        String expiredJwt = expiredIssuer.generateToken(user);

        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + expiredJwt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("invalid or expired"));
    }

    @Test
    void acceptsValidBearerFallbackTokenWithoutExtraHeaders() throws Exception {
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
    void allowsPublicRegisterAndLoginEndpointsWithoutToken() throws Exception {
        for (String path : new String[]{"/api/auth/register", "/api/auth/login"}) {
            FilterChain localChain = mock(FilterChain.class);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI(path);
            request.setMethod("POST");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, localChain);

            verify(localChain).doFilter(request, response);
            assertEquals(200, response.getStatus());
        }
    }

    private static MockHttpServletRequest apiRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/coupons");
        request.setMethod("POST");
        return request;
    }
}
