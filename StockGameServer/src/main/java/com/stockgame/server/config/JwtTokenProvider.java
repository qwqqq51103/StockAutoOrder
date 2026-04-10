package com.stockgame.server.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT Token 生成與解析工具。
 * 使用 HS256 演算法；secret 長度需 >= 256 bits (32 bytes)。
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.secretKey   = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    /** 依 username 產生 JWT */
    public String generateToken(String username) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /** 從 Token 取得 username */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /** 驗證 Token 是否有效且未過期 */
    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            String username = getUsernameFromToken(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 驗證失敗: {}", e.getMessage());
            return false;
        }
    }

    /** 檢查 Token 格式是否合法（不驗使用者） */
    public boolean isTokenMalformed(String token) {
        try {
            parseClaims(token);
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    private boolean isTokenExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
