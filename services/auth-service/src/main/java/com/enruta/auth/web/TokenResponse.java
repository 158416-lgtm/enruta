package com.enruta.auth.web;

import java.util.List;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        List<String> roles,
        UserView user) {}
