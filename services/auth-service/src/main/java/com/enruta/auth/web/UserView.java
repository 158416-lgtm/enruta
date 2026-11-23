package com.enruta.auth.web;

import com.enruta.auth.domain.User;
import java.util.List;
import java.util.UUID;

public record UserView(UUID id, String email, String fullName, List<String> roles) {

    public static UserView from(User user) {
        return new UserView(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRoles().stream().map(Enum::name).sorted().toList());
    }
}
