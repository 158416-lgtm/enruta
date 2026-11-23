package com.enruta.auth.service;

import com.enruta.auth.domain.Role;
import com.enruta.auth.domain.User;
import com.enruta.auth.repository.UserRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
 * <p>En una aplicacion real esto seria una migracion con invitacion o un alta administrativa; en la
 * demo necesitamos credenciales reproducibles para el guion de la exposicion.
 */
@Component
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private record Seed(String email, String password, String name, EnumSet<Role> roles) {}

    private static final List<Seed> SEEDS =
            List.of(
                    new Seed(
                            "admin@enruta.test",
                            "Admin1234!",
                            "Administradora EnRuta",
                            EnumSet.of(Role.ADMIN)),
                    new Seed(
                            "cliente@enruta.test",
                            "Cliente1234!",
                            "Camila Cliente",
                            EnumSet.of(Role.CLIENT)),
                    new Seed(
                            "driver1@enruta.test",
                            "Driver1234!",
                            "Ana Repartidora",
                            EnumSet.of(Role.DRIVER)),
                    new Seed(
                            "driver2@enruta.test",
                            "Driver1234!",
                            "Beto Repartidor",
                            EnumSet.of(Role.DRIVER)),
                    new Seed(
                            "driver3@enruta.test",
                            "Driver1234!",
                            "Carla Repartidora",
                            EnumSet.of(Role.DRIVER)));

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
                            email,
                            passwordEncoder.encode(seed.password()),
                            seed.name(),
                            seed.roles());
            users.save(user);
            log.info("usuario demo creado email={} roles={}", email, user.getRoles());
        }
    }
}
