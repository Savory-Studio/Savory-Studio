package com.apg.promociones.apg_api.service.LoginServices;

import com.apg.promociones.apg_api.model.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "MiClaveSuperSecretaYLoSuficientementeLargaParaHS25612345";
    private static final long EXPIRATION_MS = 86_400_000L;

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_MS);
        user = User.builder()
                .id(12)
                .firstName("Carlos")
                .lastName("Pérez")
                .email("carlos.perez@example.com")
                .build();
    }

    @Test
    void generatesSignedTokenWithExpectedClaims_CP12() {
        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));
        assertEquals(12, jwtService.extractUserId(token));
        assertEquals("carlos.perez@example.com", jwtService.extractEmail(token));
        assertEquals(86400L, jwtService.getExpirationTimeInSeconds());

        Claims claims = jwtService.extractAllClaims(token);
        assertEquals("12", claims.getSubject());
        assertEquals("Carlos", claims.get("firstName", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void rejectsTamperedTokenSignature_CP13() {
        String token = jwtService.generateToken(user);
        String tamperedToken = token.substring(0, token.length() - 4) + "XXXX";

        assertFalse(jwtService.isTokenValid(tamperedToken));
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret_CP13() {
        JwtService otherSigner = new JwtService(
                "OtraClaveSecretaDiferentePeroIgualDeLargaParaHS25699999",
                EXPIRATION_MS);
        String foreignToken = otherSigner.generateToken(user);

        assertFalse(jwtService.isTokenValid(foreignToken));
    }

    @Test
    void rejectsExpiredToken_CP14() {
        JwtService expiredJwtService = new JwtService(SECRET, -5_000L);
        String expiredToken = expiredJwtService.generateToken(user);

        assertFalse(jwtService.isTokenValid(expiredToken));
    }

    @Test
    void rejectsBlankOrMalformedTokens() {
        assertFalse(jwtService.isTokenValid(null));
        assertFalse(jwtService.isTokenValid(""));
        assertFalse(jwtService.isTokenValid("   "));
        assertFalse(jwtService.isTokenValid("not.a.jwt"));
    }
}
