package com.enruta.routing.web;

import com.enruta.routing.service.OsrmAdvancedClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operaciones de ruta que van mas alla del ETA simple.
 *
 * <p>Cuatro APIs adicionales sobre OSRM: geometria de la ruta, distancia, matriz origen-destino y
 * ajuste de un punto a la calle mas cercana. Todas responden con un plan B si OSRM no esta
 * disponible, por lo que el cliente nunca recibe un 500 por una dependencia externa.
 */
@RestController
@RequestMapping("/api/v1/eta")
public class RouteController {

    private final OsrmAdvancedClient osrm;

    public RouteController(OsrmAdvancedClient osrm) {
        this.osrm = osrm;
    }

    public record RouteResponse(
            double distanceMeters, double durationSeconds, List<double[]> geometry) {}

    public record DistanceResponse(double distanceMeters) {}

    public record MatrixRequest(
            @NotEmpty @Size(max = 25) List<@Valid Point> origins,
            @NotEmpty @Size(max = 25) List<@Valid Point> destinations) {}

    public record Point(
            @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @DecimalMin("-180.0") @DecimalMax("180.0") double lng) {}

    public record MatrixResponse(double[][] distancesMeters) {}

    public record NearestResponse(double lat, double lng) {}

    /** Ruta completa con la geometria (lista de puntos [lat, lng]) para dibujarla en el mapa. */
    @GetMapping("/route")
    public RouteResponse route(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double fromLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double fromLng,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double toLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double toLng) {
        OsrmAdvancedClient.Route route = osrm.route(fromLat, fromLng, toLat, toLng);
        return new RouteResponse(route.distanceMeters(), route.durationSeconds(), route.geometry());
    }

    /** Solo la distancia por calle, sin geometria: respuesta mas liviana. */
    @GetMapping("/distance")
    public DistanceResponse distance(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double fromLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double fromLng,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double toLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double toLng) {
        return new DistanceResponse(osrm.distance(fromLat, fromLng, toLat, toLng));
    }

    /** Matriz N x M de distancias: sirve para elegir el conductor mas cercano a varios envios. */
    @PostMapping("/matrix")
    public MatrixResponse matrix(@Valid @RequestBody MatrixRequest request) {
        List<double[]> origins =
                request.origins().stream().map(p -> new double[] {p.lat(), p.lng()}).toList();
        List<double[]> destinations =
                request.destinations().stream().map(p -> new double[] {p.lat(), p.lng()}).toList();
        return new MatrixResponse(osrm.matrix(origins, destinations));
    }

    /** Punto de la calle mas cercano a una coordenada GPS (corrige el ruido del telefono). */
    @GetMapping("/nearest")
    public NearestResponse nearest(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng) {
        double[] snapped = osrm.nearest(lat, lng);
        return new NearestResponse(snapped[0], snapped[1]);
    }
}
