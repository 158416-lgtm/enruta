package com.enruta.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void passwordEncoderUsesBcrypt() {
        PasswordEncoder encoder = config.passwordEncoder();

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(encoder.encode("secreto")).startsWith("$2a$12$");
    }

    @Test
    void jwtDecoderComesFromTheJwtService() {
        JwtService jwtService = mock(JwtService.class);
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(jwtService.decoder()).thenReturn(decoder);

        assertThat(config.jwtDecoder(jwtService)).isSameAs(decoder);
    }

    @Test
    void filterChainIsStatelessAndRequiresAuthentication() throws Exception {
        SecurityFilterChain chain = config.filterChain(httpSecurity(), mock(JwtDecoder.class));

        assertThat(chain).isNotNull();
        assertThat(chain.getFilters()).isNotEmpty();
    }

    private HttpSecurity httpSecurity() {
        ObjectPostProcessor<Object> postProcessor =
                new ObjectPostProcessor<>() {
                    @Override
                    public <O> O postProcess(O object) {
                        return object;
                    }
                };
        Map<Class<?>, Object> shared = new HashMap<>();
        // A plain (non-web) context keeps requestMatchers on its ant-path branch, so the test does
        // not need a servlet container or a HandlerMappingIntrospector.
        shared.put(ApplicationContext.class, plainApplicationContext());
        AuthenticationManagerBuilder builder = new AuthenticationManagerBuilder(postProcessor);
        return new HttpSecurity(postProcessor, builder, shared);
    }

    private ApplicationContext plainApplicationContext() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();
        return context;
    }
}
