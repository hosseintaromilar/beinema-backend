package com.coupleai.coupleai.beinema.Security;

import com.coupleai.coupleai.beinema.Exception.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Checks the ID token that "Sign in with Google" hands the browser (OpenID
 * Connect on top of OAuth 2.0): Google's signature, that it was issued for our
 * client id, and that it has not expired. Only then is the Google account
 * trusted.
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private static final Set<String> ISSUERS = Set.of(
            "https://accounts.google.com",
            "accounts.google.com"
    );

    private final String clientId;

    private volatile JwtDecoder decoder;

    @Autowired
    public GoogleTokenVerifier(@Value("${beinema.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    // tests sign tokens with their own key
    GoogleTokenVerifier(String clientId, NimbusJwtDecoder decoder) {
        this(clientId);
        decoder.setJwtValidator(validator());
        this.decoder = decoder;
    }

    public boolean isEnabled() {
        return !clientId.isEmpty();
    }

    public String clientId() {
        return clientId;
    }

    public GoogleIdentity verify(String idToken) {
        if (!isEnabled()) {
            throw new IllegalArgumentException("ورود با گوگل فعال نیست.");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidCredentialsException("ورود با گوگل ناموفق بود.");
        }

        Jwt jwt;
        try {
            jwt = decoder().decode(idToken.trim());
        } catch (JwtException exception) {
            // the reason (expired, wrong audience, keys unreachable...) goes to the log, never the token
            log.warn("Google token rejected: {}: {}", exception.getClass().getSimpleName(), exception.getMessage());
            if (String.valueOf(exception.getMessage()).contains("JWK")) {
                throw new InvalidCredentialsException("ورود با گوگل الان ممکن نیست؛ سرور به گوگل دسترسی ندارد.");
            }
            throw new InvalidCredentialsException("ورود با گوگل ناموفق بود. دوباره تلاش کنید.");
        }

        String email = jwt.getClaimAsString("email");
        if (jwt.getSubject() == null || email == null || email.isBlank()) {
            throw new InvalidCredentialsException("حساب گوگل ایمیل ندارد.");
        }
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new InvalidCredentialsException("ایمیل این حساب گوگل تأیید نشده است.");
        }

        return new GoogleIdentity(
                jwt.getSubject(),
                email.trim().toLowerCase(),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name")
        );
    }

    private JwtDecoder decoder() {
        JwtDecoder current = decoder;
        if (current == null) {
            synchronized (this) {
                if (decoder == null) {
                    NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
                    nimbus.setJwtValidator(validator());
                    decoder = nimbus;
                }
                current = decoder;
            }
        }
        return current;
    }

    private OAuth2TokenValidator<Jwt> validator() {
        OAuth2TokenValidator<Jwt> issuer = jwt -> ISSUERS.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "wrong issuer", null));
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "wrong audience", null));
        return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience);
    }

    public record GoogleIdentity(String subject, String email, String givenName, String familyName) {
    }
}
