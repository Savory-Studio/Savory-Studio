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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String RESTAURANT_ID_HEADER = "X-Restaurant-Id";
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
                Integer userId;
                try {
                    userId = jwtService.extractUserId(presentedToken);
                } catch (RuntimeException ex) {
                    writeUnauthorized(response, "The Bearer token is invalid or expired.");
                    return;
                }
                if (userId == null || userId <= 0) {
                    writeUnauthorized(response, "The Bearer token is invalid or expired.");
                    return;
                }

                String email = null;
                try {
                    email = jwtService.extractEmail(presentedToken);
                } catch (Exception ignored) {
                }

                UserPrincipal principal = new UserPrincipal(
                        userId,
                        email != null ? email : (userId + "@savory.test"),
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        principal,
                        presentedToken,
                        principal.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
                SimulatedRequestContext.set(new SimulatedCaller(userId, null));
            } else {
                Integer userId = parsePositiveInt(request.getHeader(USER_ID_HEADER));
                Integer restaurantId = parsePositiveInt(request.getHeader(RESTAURANT_ID_HEADER));

                UserPrincipal principal = new UserPrincipal(
                        userId,
                        "simulated@savory.test",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        principal,
                        presentedToken,
                        principal.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
                SimulatedRequestContext.set(new SimulatedCaller(userId, restaurantId));
            }
            filterChain.doFilter(request, response);
        } finally {
            SimulatedRequestContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private static Integer parsePositiveInt(String raw) {
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
