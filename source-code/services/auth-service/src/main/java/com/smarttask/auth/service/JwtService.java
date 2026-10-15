package com.smarttask.auth.service;

import com.smarttask.auth.domain.User;
import com.smarttask.auth.domain.UserRole;
import com.smarttask.auth.security.AdminAuthorities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final Duration accessTokenTtl;
    private final String issuer;

    public JwtService(JwtEncoder encoder,
                      @Value("${app.security.access-token-ttl:PT15M}") Duration accessTokenTtl,
                      @Value("${app.security.issuer:smart-task-auth}") String issuer) {
        this.encoder = encoder;
        this.accessTokenTtl = accessTokenTtl;
        this.issuer = issuer;
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .subject(user.getId().toString())
                .audience(List.of("smart-task-api"))
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .claim("permissions", user.getRole() == UserRole.ADMIN
                        ? AdminAuthorities.ALL
                        : List.of())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expiresInSeconds() { return accessTokenTtl.toSeconds(); }
}
