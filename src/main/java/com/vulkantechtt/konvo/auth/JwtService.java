package com.vulkantechtt.konvo.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.vulkantechtt.konvo.users.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HS256 access tokens. Tokens carry the user id (sub),
 * active tenant id, role, and full name so the JWT filter can build a
 * complete authentication without an extra DB hit on every request.
 */
@Service
public class JwtService {

    static final String CLAIM_TENANT_ID = "tid";
    static final String CLAIM_ROLE      = "role";
    static final String CLAIM_NAME      = "name";
    static final String CLAIM_EMAIL     = "email";
    static final String ISSUER          = "konvo";

    private final AuthProperties props;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtService(AuthProperties props) {
        this.props = props;
        String secret = props.getSigningSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "konvo.security.jwt.signing-secret must be set and at least 32 characters");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).withIssuer(ISSUER).build();
    }

    public String issueAccessToken(UUID userId, String email, String fullName, UUID tenantId, Role role) {
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofMinutes(props.getAccessTokenTtlMinutes()));
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(userId.toString())
                .withClaim(CLAIM_TENANT_ID, tenantId.toString())
                .withClaim(CLAIM_ROLE, role.name())
                .withClaim(CLAIM_NAME, fullName)
                .withClaim(CLAIM_EMAIL, email)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(exp))
                .sign(algorithm);
    }

    public ParsedToken parse(String token) {
        try {
            DecodedJWT decoded = verifier.verify(token);
            return new ParsedToken(
                    UUID.fromString(decoded.getSubject()),
                    decoded.getClaim(CLAIM_EMAIL).asString(),
                    decoded.getClaim(CLAIM_NAME).asString(),
                    UUID.fromString(decoded.getClaim(CLAIM_TENANT_ID).asString()),
                    Role.valueOf(decoded.getClaim(CLAIM_ROLE).asString()),
                    decoded.getExpiresAt().toInstant());
        } catch (JWTVerificationException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("Invalid access token");
        }
    }

    public int accessTokenTtlSeconds() {
        return props.getAccessTokenTtlMinutes() * 60;
    }

    public record ParsedToken(
            UUID userId,
            String email,
            String fullName,
            UUID tenantId,
            Role role,
            Instant expiresAt) {}

    public static final class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}
