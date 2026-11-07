package com.apg.promociones.apg_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Authenticates {@code /api/**} requests using a Bearer token from the {@code Authorization} header.
 * <p>
 * Until the login endpoint issues JWT tokens, the expected Bearer token is loaded from {@code .env}
 * ({@code TEST_AUTH_TOKEN} -> {@code app.simulated-auth.token}).
 */
@Component
@RequiredArgsConstructor
public class SimulatedAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SimulatedAuthProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        // Ignorar autenticación simulada para cualquier endpoint de autenticación (/api/auth/login, /api/auth/register, etc.)
        return path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String expectedToken = properties.getToken();
        if (expectedToken == null || expectedToken.isBlank()) {
            writeUnauthorized(response, "Bearer authentication is not configured. Set TEST_AUTH_TOKEN in .env.");
            return;
        }

        String presentedToken = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (presentedToken == null) {
            writeUnauthorized(response,
                    "The Bearer token is missing. Send Authorization: Bearer <token>.");
            return;
        }
        if (!expectedToken.equals(presentedToken)) {
            writeUnauthorized(response, "The Bearer token is invalid.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":401,\"message\":\"" + escapeJson(message) + "\"}");
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
