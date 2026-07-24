package com.mrboard.common.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Slf4j
public class JwtUtil {

    private static final long ACCESS_TOKEN_EXPIRE = 2 * 60 * 60 * 1000; // 2 hours
    private static final long REFRESH_TOKEN_EXPIRE = 7 * 24 * 60 * 60 * 1000; // 7 days

    private final SecretKey accessKey;
    private final SecretKey refreshKey;

    public JwtUtil(String accessSecret, String refreshSecret) {
        this.accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String username, Map<String, Object> claims) {
        return generateToken(userId, username, claims, accessKey, ACCESS_TOKEN_EXPIRE);
    }

    public String generateRefreshToken(Long userId, String username) {
        return generateToken(userId, username, null, refreshKey, REFRESH_TOKEN_EXPIRE);
    }

    private String generateToken(Long userId, String username, Map<String, Object> claims, SecretKey key, long expire) {
        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expire))
                .signWith(key);
        if (claims != null) {
            claims.forEach(builder::claim);
        }
        return builder.compact();
    }

    public Claims parseAccessToken(String token) {
        return parseToken(token, accessKey);
    }

    public Claims parseRefreshToken(String token) {
        return parseToken(token, refreshKey);
    }

    private Claims parseToken(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, accessKey);
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, refreshKey);
    }

    private boolean validateToken(String token, SecretKey key) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseAccessToken(token);
        return Long.valueOf(claims.getSubject());
    }
}
