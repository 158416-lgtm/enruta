package com.enruta.auth.security;

import com.enruta.auth.domain.User;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

/**
 * Emision y verificacion de tokens.
 *
 * <p>El par de llaves RSA se persiste en disco para que un reinicio no invalide los tokens ya
 * emitidos. La llave publica se expone en {@code /.well-known/jwks.json}, asi el gateway valida sin
 * compartir secretos (ADR-0006).
 */
@Service
public class JwtService {

    private final RSAKey rsaKey;
    private final RSASSASigner signer;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public JwtService(
            @Value("${enruta.auth.issuer}") String issuer,
            @Value("${enruta.auth.access-token-ttl:15m}") Duration accessTtl,
            @Value("${enruta.auth.refresh-token-ttl:7d}") Duration refreshTtl,
            @Value("${enruta.auth.jwk-path:keys/auth-jwk.json}") String jwkPath) {
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.rsaKey = loadOrCreateKey(Path.of(jwkPath));
        this.signer = createSigner(rsaKey);
    }

    private RSASSASigner createSigner(RSAKey key) {
        try {
            return new RSASSASigner(key);
        } catch (com.nimbusds.jose.JOSEException ex) {
            throw new IllegalStateException("No se pudo inicializar el firmante RSA", ex);
        }
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .subject(user.getId().toString())
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plus(accessTtl)))
                        .jwtID(UUID.randomUUID().toString())
                        .claim("email", user.getEmail())
                        .claim("name", user.getFullName())
                        .claim("roles", user.getRoles().stream().map(Enum::name).sorted().toList())
                        .build();
        try {
            SignedJWT jwt =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.RS256)
                                    .keyID(rsaKey.getKeyID())
                                    .type(JOSEObjectType.JWT)
                                    .build(),
                            claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar el token", ex);
        }
    }

    public String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    public JWKSet publicJwkSet() {
        return new JWKSet(rsaKey.toPublicJWK());
    }

    public List<String> jwkKeyIds() {
        return List.of(rsaKey.getKeyID());
    }

    public JwtDecoder decoder() {
        try {
            NimbusJwtDecoder decoder =
                    NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
            return decoder;
        } catch (com.nimbusds.jose.JOSEException ex) {
            throw new IllegalStateException("Llave RSA invalida para validar tokens", ex);
        }
    }

    private RSAKey loadOrCreateKey(Path path) {
        try {
            if (Files.exists(path)) {
                return RSAKey.parse(Files.readString(path));
            }
            RSAKey generated =
                    new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, generated.toJSONString());
            return generated;
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo cargar/crear la llave RSA en " + path, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Llave RSA invalida en " + path, ex);
        }
    }
}
