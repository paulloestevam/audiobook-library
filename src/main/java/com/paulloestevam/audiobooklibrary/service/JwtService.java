package com.paulloestevam.audiobooklibrary.service;

import com.paulloestevam.audiobooklibrary.model.User;
import com.paulloestevam.audiobooklibrary.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    private final UserRepository userRepository;

    public String generateToken(Authentication authentication) {
        DefaultOAuth2User defaultOAuth2User = (DefaultOAuth2User) authentication.getPrincipal();
        String email = defaultOAuth2User.getAttribute("email");

        Optional<User> userOpt = userRepository.findByEmail(email);
        boolean isAdmin = userOpt.map(User::isAdmin).orElse(false);
        boolean canAccessRestrictedContent = userOpt.map(User::isCanAccessRestrictedContent).orElse(false);

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("name", defaultOAuth2User.getAttribute("name"));
        claims.put("picture", defaultOAuth2User.getAttribute("picture"));
        claims.put("admin", isAdmin);
        claims.put("canAccessRestrictedContent", canAccessRestrictedContent);

        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes()))
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.error("Token JWT inválido: {}", e.getMessage());
            return false;
        }
    }

    public boolean isAdmin(String token) {
        Boolean isAdmin = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("admin", Boolean.class);

        return Boolean.TRUE.equals(isAdmin); // Garante false se for null
    }
}
