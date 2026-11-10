package com.apg.promociones.apg_api.service.LoginServices;

import com.apg.promociones.apg_api.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Service responsible for issuing and validating HS256 JSON Web Tokens (JWT).
 */
@Service
public class JwtService {

    @Value("${jwt.secret:MiClaveSuperSecretaYLoSuficientementeLargaParaHS25612345}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public JwtService() {
    }

    public JwtService(String secretKey, long jwtExpiration) {
        this.secretKey = secretKey;
        this.jwtExpiration = jwtExpiration;
    }

    public String generateToken(User user) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("email", user.getEmail());
        extraClaims.put("firstName", user.getFirstName());

        long nowMillis = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(user.getId().toString())
                .setIssuedAt(new Date(nowMillis))
                .setExpiration(new Date(nowMillis + jwtExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validates the JWT signature, structure, and expiration timestamp using the configured secret key.
     */
    public boolean isTokenValid(String token) {
        if (token == null || token.isBlank() || secretKey == null || secretKey.isBlank()) {
            return false;
        }
        try {
            Claims claims = extractAllClaims(token);
            Date expiration = claims.getExpiration();
            return claims.getSubject() != null
                    && !claims.getSubject().isBlank()
                    && (expiration == null || expiration.after(new Date()));
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Extracts all verified claims from a signed JWT token.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the authenticated user ID stored in the JWT {@code sub} claim.
     */
    public Integer extractUserId(String token) {
        String subject = extractAllClaims(token).getSubject();
        return Integer.valueOf(subject);
    }

    /**
     * Extracts the user email claim from the JWT payload.
     */
    public String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }

    public long getExpirationTimeInSeconds() {
        return jwtExpiration / 1000;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
