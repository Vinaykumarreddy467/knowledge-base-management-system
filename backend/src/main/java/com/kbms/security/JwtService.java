package com.kbms.security;

import com.kbms.config.AppProperties;
import com.kbms.common.ApiException;
import com.kbms.user.AppUser;
import com.kbms.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String DEV_SECRET = "kbms-development-only-secret-key-change-me-32";

    private final UserRepository users;
    private final AppProperties properties;
    private final SecretKey key;

    public JwtService(UserRepository users, AppProperties properties) {
        this.users = users;
        this.properties = properties;
        String secret = properties.security().jwtSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("KBMS_JWT_SECRET is not set - using an ephemeral development key. "
                    + "All issued tokens become invalid on restart. Set it before any real deployment.");
            this.key = Keys.hmacShaKeyFor(DEV_SECRET.getBytes(StandardCharsets.UTF_8));
        } else {
            if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("KBMS_JWT_SECRET must be at least 32 bytes long");
            }
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.security().jwtTtlMinutes() * 60L);
        return Jwts.builder()
                .subject(user.getEmail())
                .claims(Map.of("uid", user.getId(), "role", user.getRole().name()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public long expiresInSeconds() {
        return properties.security().jwtTtlMinutes() * 60L;
    }

    /** @throws ApiException when the token is invalid, expired, or points at a disabled user. */
    public AppUser resolve(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(401, "INVALID_TOKEN", "Access token is invalid or expired");
        }
        var user = users.findByEmailIgnoreCase(claims.getSubject())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException(401, "INVALID_TOKEN", "User is no longer active"));
        if (!user.getRole().name().equals(claims.get("role", String.class)) || user.getId() == null) {
            throw new ApiException(401, "INVALID_TOKEN", "Access token is no longer valid");
        }
        return user;
    }
}
