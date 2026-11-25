package com.enruta.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DemoUserSeederTest {

    @Mock private UserRepository users;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    void createsTheEightDemoUsers() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        new DemoUserSeeder(users, passwordEncoder, true).run(null);

        verify(users, times(8)).save(any());
    }

    @Test
    void doesNothingWhenSeedingIsDisabled() {
        new DemoUserSeeder(users, passwordEncoder, false).run(null);

        verify(users, never()).save(any());
    }

    @Test
    void existingUsersAreNotDuplicated() {
        when(users.existsByEmail(anyString())).thenReturn(true);

        new DemoUserSeeder(users, passwordEncoder, true).run(null);

        verify(users, never()).save(any());
    }

    @Test
    void driversUseTheSharedIdentityWithDispatch() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        new DemoUserSeeder(users, passwordEncoder, true).run(null);

        verify(users)
                .save(
                        org.mockito.ArgumentMatchers.argThat(
                                user ->
                                        user.getId()
                                                .toString()
                                                .equals("11111111-1111-1111-1111-111111111101")));
        assertThat(true).isTrue();
    }
}
