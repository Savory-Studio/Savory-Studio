package com.apg.promociones.apg_api.service.LoginServices;

import com.apg.promociones.apg_api.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    // Lee la clave secreta desde application.properties / .env
    @Value("${jwt.secret}")
    private String secretKey;

    // Tiempo de expiración en milisegundos (ej: 86400000 ms = 24 horas)
    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public String generateToken(User user) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("email", user.getEmail());
        extraClaims.put("firstName", user.getFirstName());

        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(user.getId().toString()) // No usamos X-User-Id, va en el subject del token
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public long getExpirationTimeInSeconds() {
        return jwtExpiration / 1000;
    }

    private Key getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
