package sme.tech.innovators.sme.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sme.tech.innovators.sme.entity.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expiry-minutes:1440}")
    private int expiryMinutes;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        long nowMs = System.currentTimeMillis();
        long expiryMs = nowMs + (long) expiryMinutes * 60 * 1000;

        String businessId = (user.getBusiness() != null)
                ? user.getBusiness().getId().toString() : null;

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("userId", user.getId().toString())
                .claim("businessId", businessId)
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(new Date(nowMs))
                .expiration(new Date(expiryMs))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaims(token).get("userId", String.class));
    }

    public UUID extractBusinessId(String token) {
        String bid = extractClaims(token).get("businessId", String.class);
        return bid != null ? UUID.fromString(bid) : null;
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }
}
