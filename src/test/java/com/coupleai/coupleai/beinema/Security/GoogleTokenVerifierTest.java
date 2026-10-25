package com.coupleai.coupleai.beinema.Security;

import com.coupleai.coupleai.beinema.Exception.InvalidCredentialsException;
import com.coupleai.coupleai.beinema.Security.GoogleTokenVerifier.GoogleIdentity;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleTokenVerifierTest {

    private static final String CLIENT = "client-123.apps.googleusercontent.com";

    private static KeyPair googleKey;
    private static KeyPair otherKey;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        googleKey = generator.generateKeyPair();
        otherKey = generator.generateKeyPair();
    }

    private GoogleTokenVerifier verifier() {
        return new GoogleTokenVerifier(
                CLIENT,
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) googleKey.getPublic()).build()
        );
    }

    private static String token(KeyPair key, Consumer<JWTClaimsSet.Builder> change) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT)
                .subject("10987654321")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("email", "Sara@Example.com")
                .claim("email_verified", true)
                .claim("given_name", "Sara")
                .claim("family_name", "Ahmadi");
        change.accept(claims);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(key.getPrivate()));
        return jwt.serialize();
    }

    @Test
    void acceptsAValidGoogleToken() throws Exception {
        GoogleIdentity identity = verifier().verify(token(googleKey, claims -> { }));

        assertEquals("10987654321", identity.subject());
        assertEquals("sara@example.com", identity.email());
        assertEquals("Sara", identity.givenName());
        assertEquals("Ahmadi", identity.familyName());
    }

    @Test
    void acceptsTheShortIssuerToo() throws Exception {
        verifier().verify(token(googleKey, claims -> claims.issuer("accounts.google.com")));
    }

    @Test
    void rejectsATokenSignedByAnotherKey() {
        assertThrows(InvalidCredentialsException.class,
                () -> verifier().verify(token(otherKey, claims -> { })));
    }

    @Test
    void rejectsATokenForAnotherClient() {
        assertThrows(InvalidCredentialsException.class,
                () -> verifier().verify(token(googleKey, claims -> claims.audience("someone-else"))));
    }

    @Test
    void rejectsAnotherIssuer() {
        assertThrows(InvalidCredentialsException.class,
                () -> verifier().verify(token(googleKey, claims -> claims.issuer("https://evil.example"))));
    }

    @Test
    void rejectsAnExpiredToken() {
        assertThrows(InvalidCredentialsException.class,
                () -> verifier().verify(token(googleKey, claims -> claims
                        .issueTime(Date.from(Instant.now().minusSeconds(7200)))
                        .expirationTime(Date.from(Instant.now().minusSeconds(3600))))));
    }

    @Test
    void rejectsAnUnverifiedEmail() {
        assertThrows(InvalidCredentialsException.class,
                () -> verifier().verify(token(googleKey, claims -> claims.claim("email_verified", false))));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(InvalidCredentialsException.class, () -> verifier().verify("not-a-token"));
        assertThrows(InvalidCredentialsException.class, () -> verifier().verify(" "));
    }

    @Test
    void isOffWithoutAClientId() {
        GoogleTokenVerifier off = new GoogleTokenVerifier("");

        assertFalse(off.isEnabled());
        assertThrows(IllegalArgumentException.class, () -> off.verify("anything"));
    }
}
