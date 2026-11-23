package com.enruta.auth.service;

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
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserView register(RegisterRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw BusinessException.conflict("EMAIL_TAKEN", "El correo ya esta registrado");
        }
        User user =
                users.save(
                        new User(
                                email,
                                passwordEncoder.encode(request.password()),
                                request.fullName(),
                                EnumSet.of(Role.CLIENT)));
        return UserView.from(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user =
                users.findByEmail(request.email().toLowerCase(Locale.ROOT))
                        .orElseThrow(this::invalidCredentials);
        if (!user.isEnabled()
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return issueTokens(user);
    }

    /** Rotacion: el refresh usado queda revocado y se emite uno nuevo. */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken stored =
                refreshTokens
                        .findByTokenHash(jwtService.hash(rawRefreshToken))
                        .orElseThrow(this::invalidRefresh);
        if (!stored.isUsable(Instant.now())) {
            throw invalidRefresh();
        }
        stored.revoke();
        User user =
                users.findById(stored.getUserId())
                        .orElseThrow(
                                () ->
                                        BusinessException.notFound(
                                                "USER_NOT_FOUND", "Usuario inexistente"));
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens
                .findByTokenHash(jwtService.hash(rawRefreshToken))
                .ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public UserView currentUser(UUID userId) {
        return users.findById(userId)
                .map(UserView::from)
                .orElseThrow(
                        () -> BusinessException.notFound("USER_NOT_FOUND", "Usuario inexistente"));
    }

    private TokenResponse issueTokens(User user) {
        String access = jwtService.issueAccessToken(user);
        String rawRefresh = jwtService.newRefreshToken();
        refreshTokens.save(
                new RefreshToken(
                        user.getId(),
                        jwtService.hash(rawRefresh),
                        Instant.now().plus(jwtService.refreshTtl())));
        return new TokenResponse(
                access,
                rawRefresh,
                "Bearer",
                jwtService.accessTtl().toSeconds(),
                user.getRoles().stream().map(Enum::name).sorted().toList(),
                UserView.from(user));
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(
                "INVALID_CREDENTIALS", "Correo o contrasena incorrectos", HttpStatus.UNAUTHORIZED);
    }

    private BusinessException invalidRefresh() {
        return new BusinessException(
                "INVALID_REFRESH_TOKEN",
                "Refresh token invalido o expirado",
                HttpStatus.UNAUTHORIZED);
    }
}
