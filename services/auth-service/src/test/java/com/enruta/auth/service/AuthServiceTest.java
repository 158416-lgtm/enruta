package com.enruta.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.auth.domain.RefreshToken;
import com.enruta.auth.domain.Role;
import com.enruta.auth.domain.User;
import com.enruta.auth.repository.RefreshTokenRepository;
import com.enruta.auth.repository.UserRepository;
import com.enruta.auth.security.JwtService;
import com.enruta.auth.web.LoginRequest;
import com.enruta.auth.web.RegisterRequest;
import com.enruta.auth.web.TokenResponse;
import com.enruta.auth.web.UserView;
import com.enruta.common.error.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository users;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, refreshTokens, passwordEncoder, jwtService);
    }

    private User client() {
        return new User(
                UUID.randomUUID(),
                "cliente@enruta.test",
                "hash",
                "Camila",
                EnumSet.of(Role.CLIENT));
    }

    private void stubTokenIssuing() {
        when(jwtService.issueAccessToken(any())).thenReturn("access-jwt");
        when(jwtService.newRefreshToken()).thenReturn("refresh-raw");
        when(jwtService.hash("refresh-raw")).thenReturn("refresh-hash");
        when(jwtService.accessTtl()).thenReturn(Duration.ofMinutes(15));
        when(jwtService.refreshTtl()).thenReturn(Duration.ofDays(7));
    }

    @Test
    void registerHashesThePasswordAndAssignsClientRole() {
        when(users.existsByEmail("cliente@enruta.test")).thenReturn(false);
        when(passwordEncoder.encode("Cliente1234!")).thenReturn("bcrypt");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserView view =
                service.register(
                        new RegisterRequest("Cliente@EnRuta.test", "Cliente1234!", "Camila"));

        assertThat(view.email()).isEqualTo("cliente@enruta.test");
        assertThat(view.roles()).containsExactly("CLIENT");
        verify(passwordEncoder).encode("Cliente1234!");
    }

    @Test
    void registerRejectsDuplicatedEmail() {
        when(users.existsByEmail("cliente@enruta.test")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.register(
                                        new RegisterRequest(
                                                "cliente@enruta.test", "x12345678", "X")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("EMAIL_TAKEN");
        verify(users, never()).save(any());
    }

    @Test
    void loginReturnsAccessAndRefreshTokens() {
        User user = client();
        when(users.findByEmail("cliente@enruta.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Cliente1234!", "hash")).thenReturn(true);
        stubTokenIssuing();

        TokenResponse response =
                service.login(new LoginRequest("cliente@enruta.test", "Cliente1234!"));

        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-raw");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.roles()).containsExactly("CLIENT");
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void loginRejectsWrongPassword() {
        when(users.findByEmail("cliente@enruta.test")).thenReturn(Optional.of(client()));
        when(passwordEncoder.matches("mala", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("cliente@enruta.test", "mala")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getStatus().value())
                .isEqualTo(401);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void loginRejectsDisabledUsers() {
        User disabled = org.mockito.Mockito.mock(User.class);
        when(disabled.isEnabled()).thenReturn(false);
        when(users.findByEmail("cliente@enruta.test")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(
                        () ->
                                service.login(
                                        new LoginRequest("cliente@enruta.test", "Cliente1234!")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refreshRotatesTheToken() {
        User user = client();
        RefreshToken stored =
                new RefreshToken(user.getId(), "refresh-hash", Instant.now().plusSeconds(3600));
        when(jwtService.hash("refresh-raw")).thenReturn("refresh-hash");
        when(refreshTokens.findByTokenHash("refresh-hash")).thenReturn(Optional.of(stored));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        stubTokenIssuing();

        service.refresh("refresh-raw");

        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void refreshRejectsUnknownTokens() {
        when(jwtService.hash("raro")).thenReturn("hash-raro");
        when(refreshTokens.findByTokenHash("hash-raro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("raro"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void refreshRejectsExpiredTokens() {
        RefreshToken expired =
                new RefreshToken(UUID.randomUUID(), "refresh-hash", Instant.now().minusSeconds(60));
        when(jwtService.hash("refresh-raw")).thenReturn("refresh-hash");
        when(refreshTokens.findByTokenHash("refresh-hash")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.refresh("refresh-raw"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        RefreshToken stored =
                new RefreshToken(UUID.randomUUID(), "refresh-hash", Instant.now().plusSeconds(60));
        when(jwtService.hash("refresh-raw")).thenReturn("refresh-hash");
        when(refreshTokens.findByTokenHash("refresh-hash")).thenReturn(Optional.of(stored));

        service.logout("refresh-raw");

        assertThat(stored.isRevoked()).isTrue();
    }

    @Test
    void currentUserFailsWhenItDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.currentUser(id))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("USER_NOT_FOUND");
    }
}
