package com.apg.promociones.apg_api.security;

import com.apg.promociones.apg_api.service.LoginServices.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Authenticates protected {@code /api/**} requests using a Bearer token from the {@code Authorization} header.
 * <p>
 * Public authentication endpoints under {@code /api/auth/**} ({@code /api/auth/register}, {@code /api/auth/login})
 * and OpenAPI documentation routes are excluded from filtering.
 * <p>
 * Protected routes accept either a signed, non-expired HS256 JWT issued by {@link JwtService}
 * or the fallback token configured via {@code TEST_AUTH_TOKEN} ({@code app.simulated-auth.token}).
 */
@Component
public class SimulatedAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SimulatedAuthProperties properties;
    private final JwtService jwtService;

    public SimulatedAuthFilter(SimulatedAuthProperties properties) {
        this(properties, null);
    }

    @Autowired
    public SimulatedAuthFilter(
            SimulatedAuthProperties properties,
            @Autowired(required = false) JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        return path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String expectedToken = properties.getToken();
        boolean hasConfiguredFallbackToken = expectedToken != null && !expectedToken.isBlank();
        if (jwtService == null && !hasConfiguredFallbackToken) {
            writeUnauthorized(response, "Bearer authentication is not configured. Set TEST_AUTH_TOKEN in .env.");
            return;
        }

        String presentedToken = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (presentedToken == null) {
            writeUnauthorized(response,
                    "The Bearer token is missing. Send Authorization: Bearer <token>.");
            return;
        }

        boolean validJwt = jwtService != null && jwtService.isTokenValid(presentedToken);
        boolean validFallbackToken = hasConfiguredFallbackToken && expectedToken.equals(presentedToken);

        if (!validJwt && !validFallbackToken) {
            writeUnauthorized(response, "The Bearer token is invalid or expired.");
            return;
        }

        try {
            if (validJwt) {
                try {
                    Integer userId = jwtService.extractUserId(presentedToken);
                    SimulatedRequestContext.set(new SimulatedCaller(userId, null));
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userId, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (RuntimeException ignored) {
                    // Subject is non-numeric; authentication still succeeds for valid signed JWT
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            SimulatedRequestContext.clear();
            SecurityContextHolder.clearContext();
        }
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
