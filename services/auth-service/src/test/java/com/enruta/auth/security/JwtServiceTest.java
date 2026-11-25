package com.enruta.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enruta.auth.domain.Role;
import com.enruta.auth.domain.User;
import com.nimbusds.jwt.SignedJWT;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class JwtServiceTest {

    @TempDir Path tempDir;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService =
                new JwtService(
                        "http://localhost:18080",
                        java.time.Duration.ofMinutes(15),
                        java.time.Duration.ofDays(7),
                        tempDir.resolve("jwk.json").toString());
    }

    @Test
    void issuedTokenCarriesSubjectAndRoles() throws Exception {
        User user =
                new User("ana@enruta.test", "hash", "Ana", EnumSet.of(Role.DRIVER, Role.CLIENT));

        String token = jwtService.issueAccessToken(user);
        SignedJWT parsed = SignedJWT.parse(token);

        assertThat(parsed.getJWTClaimsSet().getSubject()).isEqualTo(user.getId().toString());
        assertThat(parsed.getJWTClaimsSet().getIssuer()).isEqualTo("http://localhost:18080");
        assertThat(parsed.getJWTClaimsSet().getStringListClaim("roles"))
                .containsExactly("CLIENT", "DRIVER");
        assertThat(parsed.getHeader().getKeyID()).isNotBlank();
    }

    @Test
    void decoderValidatesOwnTokensAndRejectsForeignIssuer() throws Exception {
        User user = new User("beto@enruta.test", "hash", "Beto", EnumSet.of(Role.CLIENT));
        String token = jwtService.issueAccessToken(user);

        JwtDecoder decoder = jwtService.decoder();
        Jwt decoded = decoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo(user.getId().toString());
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("CLIENT");

        JwtService otherIssuer =
                new JwtService(
                        "http://otro.example",
                        java.time.Duration.ofMinutes(15),
                        java.time.Duration.ofDays(7),
                        tempDir.resolve("otro.json").toString());
        String foreign = otherIssuer.issueAccessToken(user);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> decoder.decode(foreign)))
                .isNotNull();
    }

    @Test
    void jwksExposesOnlyThePublicKey() {
        var jwks = jwtService.publicJwkSet();

        assertThat(jwks.getKeys()).hasSize(1);
        assertThat(jwks.getKeys().getFirst().isPrivate()).isFalse();
        assertThat(jwks.toJSONObject().toString()).doesNotContain("\"d\"");
        assertThat(jwks.getKeys().getFirst().getKeyID()).isIn(jwtService.jwkKeyIds());
    }

    @Test
    void refreshTokensAreRandomAndHashed() {
        String first = jwtService.newRefreshToken();
        String second = jwtService.newRefreshToken();

        assertThat(first).isNotEqualTo(second);
        assertThat(jwtService.hash(first)).hasSize(64).isNotEqualTo(first);
        assertThat(jwtService.hash(first)).isEqualTo(jwtService.hash(first));
    }

    @Test
    void keyIsReusedAcrossRestarts() {
        String path = tempDir.resolve("persistida.json").toString();
        JwtService first =
                new JwtService(
                        "http://localhost:18080",
                        java.time.Duration.ofMinutes(15),
                        java.time.Duration.ofDays(7),
                        path);
        JwtService second =
                new JwtService(
                        "http://localhost:18080",
                        java.time.Duration.ofMinutes(15),
                        java.time.Duration.ofDays(7),
                        path);

        assertThat(second.jwkKeyIds()).isEqualTo(first.jwkKeyIds());
    }

    @Test
    void exposesTheConfiguredTtls() {
        assertThat(jwtService.accessTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(jwtService.refreshTtl()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void unreadableKeyPathFailsWithUncheckedIoException() throws Exception {
        Path directory = tempDir.resolve("como-directorio");
        Files.createDirectories(directory);

        assertThatThrownBy(
                        () ->
                                new JwtService(
                                        "http://localhost:18080",
                                        Duration.ofMinutes(15),
                                        Duration.ofDays(7),
                                        directory.toString()))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void corruptedKeyFileFailsFast() throws Exception {
        Path file = tempDir.resolve("rota.json");
        Files.writeString(file, "esto no es una llave RSA");

        assertThatThrownBy(
                        () ->
                                new JwtService(
                                        "http://localhost:18080",
                                        Duration.ofMinutes(15),
                                        Duration.ofDays(7),
                                        file.toString()))
                .isInstanceOf(IllegalStateException.class);
    }
}
