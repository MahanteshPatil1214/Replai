package com.replai.tickets.security;

import com.replai.tickets.model.Employee;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and reads the HS256 JWTs used by the API. The same secret must be
 * configured in ai-service, which reads the token to protect KB writes.
 */
@Service
public class JwtService {

    /** HS256 needs at least 32 bytes of key material. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expiryMillis;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiry-minutes:480}") long expiryMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " characters - set the JWT_SECRET environment variable");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiryMillis = expiryMinutes * 60_000L;
    }

    public String issue(Employee employee) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(employee.getId()))
                .claim("name", employee.getName())
                .claim("email", employee.getEmail())
                .claim("role", employee.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiryMillis)))
                .signWith(key)
                .compact();
    }

    /**
     * @return the employee id inside the token, or null when the token is
     * missing, expired or signed with another key.
     */
    public Long parseEmployeeId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
