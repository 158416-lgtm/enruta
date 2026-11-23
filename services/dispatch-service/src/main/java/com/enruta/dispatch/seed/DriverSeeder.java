package com.enruta.dispatch.seed;

import com.enruta.dispatch.domain.Driver;
import com.enruta.dispatch.domain.DriverStatus;
import com.enruta.dispatch.repository.DriverRepository;
import com.enruta.dispatch.service.CandidateFinder;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conductores de demo con UUID fijos.
 *
 * <p>Los mismos UUID existen como usuarios en auth: el id es la identidad compartida, asi el
 * conductor puede autenticarse y dispatch sabe quien es sin duplicar credenciales.
 */
@Component
public class DriverSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DriverSeeder.class);
    public static final List<UUID> DRIVER_IDS =
            List.of(
                    UUID.fromString("11111111-1111-1111-1111-111111111101"),
                    UUID.fromString("11111111-1111-1111-1111-111111111102"),
                    UUID.fromString("11111111-1111-1111-1111-111111111103"),
                    UUID.fromString("11111111-1111-1111-1111-111111111104"),
                    UUID.fromString("11111111-1111-1111-1111-111111111105"),
                    UUID.fromString("11111111-1111-1111-1111-111111111106"));

    private record Seed(
            UUID id, String name, String vehicle, double rating, double lat, double lng) {}

    private static final List<Seed> SEEDS =
            List.of(
                    new Seed(DRIVER_IDS.get(0), "Ana Repartidora", "MOTO", 4.9, 4.3449, -74.3674),
                    new Seed(DRIVER_IDS.get(1), "Beto Repartidor", "MOTO", 4.7, 4.3400, -74.3620),
                    new Seed(DRIVER_IDS.get(2), "Carla Repartidora", "BICI", 4.5, 4.3480, -74.3700),
                    new Seed(DRIVER_IDS.get(3), "Diego Repartidor", "MOTO", 4.3, 4.3350, -74.3650),
                    new Seed(
                            DRIVER_IDS.get(4), "Elena Repartidora", "CARRO", 4.8, 4.3520, -74.3600),
                    new Seed(DRIVER_IDS.get(5), "Fabio Repartidor", "MOTO", 4.2, 4.3380, -74.3720));

    private final DriverRepository drivers;
    private final CandidateFinder finder;
    private final boolean enabled;

    public DriverSeeder(
            DriverRepository drivers,
            CandidateFinder finder,
            @Value("${enruta.dispatch.seed:true}") boolean enabled) {
        this.drivers = drivers;
        this.finder = finder;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        for (Seed seed : SEEDS) {
            if (drivers.existsById(seed.id())) {
                continue;
            }
            Driver driver =
                    new Driver(
                            seed.id(),
                            seed.name(),
                            seed.vehicle(),
                            seed.rating(),
                            seed.lat(),
                            seed.lng());
            drivers.save(driver);
            log.info("conductor demo creado id={} nombre={}", seed.id(), seed.name());
        }

        // El indice geografico vive en Redis y puede perderse (reinicio sin persistencia).
        // Se reconstruye desde la base: es derivable, no fuente de verdad.
        drivers.findByStatus(DriverStatus.AVAILABLE).stream()
                .filter(driver -> driver.getCurrentLat() != null && driver.getCurrentLng() != null)
                .forEach(
                        driver ->
                                finder.index(
                                        driver.getId(),
                                        driver.getCurrentLat(),
                                        driver.getCurrentLng()));
    }
}
