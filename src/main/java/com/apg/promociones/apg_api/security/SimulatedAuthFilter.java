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
import java.util.Base64;

/**
 * Temporary identity layer until JWT login exists.
 * Expected headers on /api/**:
 *   Authorization: Bearer &lt;test-token&gt;  (or Basic with password = test-token)
 *   X-User-Id: &lt;integer&gt;
 *   X-Restaurant-Id: &lt;integer&gt;  (optional, restaurant-scoped operations)
 */
@Component
@RequiredArgsConstructor
public class SimulatedAuthFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String RESTAURANT_ID_HEADER = "X-Restaurant-Id";

    private final SimulatedAuthProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request.getHeader(HttpHeaders.AUTHORIZATION));
            if (token == null) {
                writeUnauthorized(response, "The test token is missing. Please send Authorization: Bearer or Basic.");
                return;
            }
            if (!properties.getToken().equals(token)) {
                writeUnauthorized(response, "The test token is invalid.");
                return;
            }

            Integer userId = parsePositiveInt(request.getHeader(USER_ID_HEADER));
            if (userId == null) {
                writeUnauthorized(response, "The X-User-Id header is missing or invalid.");
                return;
            }

            Integer restaurantId = null;
            String restaurantHeader = request.getHeader(RESTAURANT_ID_HEADER);
            if (restaurantHeader != null && !restaurantHeader.isBlank()) {
                restaurantId = parsePositiveInt(restaurantHeader);
                if (restaurantId == null) {
                    writeUnauthorized(response, "The X-Restaurant-Id header is invalid.");
                    return;
                }
            }

            SimulatedRequestContext.set(new SimulatedCaller(userId, restaurantId));
            filterChain.doFilter(request, response);
        } finally {
            SimulatedRequestContext.clear();
        }
    }

    private String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String bearer = authorization.substring(7).trim();
            return bearer.isEmpty() ? null : bearer;
        }
        if (authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            String encoded = authorization.substring(6).trim();
            if (encoded.isEmpty()) {
                return null;
            }
            try {
                String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                int separator = decoded.indexOf(':');
                if (separator < 0) {
                    return decoded.isBlank() ? null : decoded;
                }
                String password = decoded.substring(separator + 1);
                return password.isBlank() ? null : password;
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return null;
    }

    private Integer parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
        String body = "{\"status\":401,\"message\":\"" + escaped + "\"}";
        response.getWriter().write(body);
        response.getWriter().flush();
    }
}
