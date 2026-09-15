package com.coupleai.coupleai.beinema.Security;

import com.coupleai.coupleai.beinema.Entity.RevokedToken;
import com.coupleai.coupleai.beinema.Repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RevokedTokenRepository revokedTokenRepository;

    private final JwtService jwtService;

    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        String normalized = token.trim();

        if (!jwtService.isTokenValid(normalized)) {
            return;
        }

        String tokenHash = hash(normalized);
        if (revokedTokenRepository.existsByTokenHash(tokenHash)) {
            return;
        }

        Date expiration = jwtService.extractExpiration(normalized);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                expiration.toInstant(),
                ZoneId.systemDefault()
        );

        revokedTokenRepository.save(
                RevokedToken.builder()
                        .tokenHash(tokenHash)
                        .expiresAt(expiresAt)
                        .build()
        );

        revokedTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        return revokedTokenRepository.existsByTokenHash(hash(token.trim()));
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("امکان هش کردن توکن وجود ندارد.", exception);
        }
    }
}
