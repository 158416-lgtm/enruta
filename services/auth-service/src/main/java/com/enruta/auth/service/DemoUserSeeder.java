package com.enruta.auth.service;

import com.enruta.auth.domain.Role;
import com.enruta.auth.domain.User;
import com.enruta.auth.repository.UserRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usuarios de demo, idempotente.
 *
 * <p>Los conductores usan UUID fijos que coinciden con los de dispatch: auth posee credenciales,
 * dispatch posee perfil y disponibilidad, y el id es la identidad compartida.
 */
@Component
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private record Seed(UUID id, String email, String password, String name, EnumSet<Role> roles) {}

    private static final List<Seed> SEEDS =
            List.of(
                    new Seed(
                            null,
                            "admin@enruta.test",
                            "Admin1234!",
                            "Administradora EnRuta",
                            EnumSet.of(Role.ADMIN)),
                    new Seed(
                            null,
                            "cliente@enruta.test",
                            "Cliente1234!",
                            "Camila Cliente",
                            EnumSet.of(Role.CLIENT)),
                    driver(1, "Ana Repartidora"),
                    driver(2, "Beto Repartidor"),
                    driver(3, "Carla Repartidora"),
                    driver(4, "Diego Repartidor"),
                    driver(5, "Elena Repartidora"),
                    driver(6, "Fabio Repartidor"));

    private static Seed driver(int number, String name) {
        return new Seed(
                UUID.fromString("11111111-1111-1111-1111-11111111110" + number),
                "driver" + number + "@enruta.test",
                "Driver1234!",
                name,
                EnumSet.of(Role.DRIVER));
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;

    public DemoUserSeeder(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${enruta.auth.seed:true}") boolean enabled) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        for (Seed seed : SEEDS) {
            String email = seed.email().toLowerCase(Locale.ROOT);
            if (users.existsByEmail(email)) {
                continue;
            }
            User user =
                    new User(
                            seed.id() == null ? UUID.randomUUID() : seed.id(),
                            email,
                            passwordEncoder.encode(seed.password()),
                            seed.name(),
                            seed.roles());
            users.save(user);
            log.info("usuario demo creado email={} roles={}", email, user.getRoles());
        }
    }
}
