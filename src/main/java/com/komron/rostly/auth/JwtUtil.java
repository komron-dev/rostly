package com.komron.rostly.auth;

import com.komron.rostly.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public String generateAccessToken(UUID userId, String role) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("rostly")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getAccessTokenExpiry()))
                .claim("role", role)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // Opaque token — not a JWT. Gets hashed and stored in DB.
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    // General-purpose short-lived JWT for email flows — never for auth
    public String generateEmailToken(UUID userId, long expirySeconds, String purpose, Map<String, String> extraClaims) {
        Instant now = Instant.now();

        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer("rostly")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirySeconds))
                .claim("purpose", purpose);

        extraClaims.forEach(builder::claim);

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
    }
}