package com.enruta.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.auth.security.JwtService;
import com.enruta.auth.service.AuthService;
import com.nimbusds.jose.jwk.JWKSet;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthControllerTest {

    private AuthService authService;
    private JwtService jwtService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        jwtService = mock(JwtService.class);
        controller = new AuthController(authService, jwtService);
    }

    @Test
    void registerReturnsCreatedWithTheUserLocation() {
        UUID id = UUID.randomUUID();
        UserView view = new UserView(id, "ana@enruta.test", "Ana", List.of("CLIENT"));
        when(authService.register(any(RegisterRequest.class))).thenReturn(view);

        ResponseEntity<UserView> response =
                controller.register(new RegisterRequest("ana@enruta.test", "Clave1234!", "Ana"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("/api/v1/auth/users/" + id));
        assertThat(response.getBody()).isSameAs(view);
    }

    @Test
    void loginDelegatesToTheService() {
        TokenResponse tokens =
                new TokenResponse(
                        "access",
                        "refresh",
                        "Bearer",
                        900,
                        List.of("CLIENT"),
                        new UserView(
                                UUID.randomUUID(), "ana@enruta.test", "Ana", List.of("CLIENT")));
        when(authService.login(any(LoginRequest.class))).thenReturn(tokens);

        assertThat(controller.login(new LoginRequest("ana@enruta.test", "Clave1234!")))
                .isSameAs(tokens);
    }

    @Test
    void refreshDelegatesTheRawToken() {
        TokenResponse tokens = mock(TokenResponse.class);
        when(authService.refresh("raw-refresh")).thenReturn(tokens);

        assertThat(controller.refresh(new RefreshRequest("raw-refresh"))).isSameAs(tokens);
    }

    @Test
    void logoutRevokesAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.logout(new RefreshRequest("raw-refresh"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).logout("raw-refresh");
    }

    @Test
    void jwksReturnsThePublicJsonObject() {
        JWKSet jwkSet = mock(JWKSet.class);
        Map<String, Object> json = Map.of("keys", List.of());
        when(jwtService.publicJwkSet()).thenReturn(jwkSet);
        when(jwkSet.toJSONObject()).thenReturn(json);

        assertThat(controller.jwks()).isSameAs(json);
    }

    @Test
    void meResolvesTheProfileFromTheTokenSubject() {
        UUID id = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(id.toString());
        UserView view = new UserView(id, "ana@enruta.test", "Ana", List.of("CLIENT"));
        when(authService.currentUser(id)).thenReturn(view);

        assertThat(controller.me(jwt)).isSameAs(view);
    }

    @Test
    void refreshRequestExposesTheToken() {
        assertThat(new RefreshRequest("raw-refresh").refreshToken()).isEqualTo("raw-refresh");
    }
}
