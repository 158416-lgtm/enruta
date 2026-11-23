package com.enruta.routing.service;

/** Resultado de una consulta de ruta. {@code source} deja ver si vino de OSRM, caché o plan B. */
public record RouteResult(
        long distanceMeters, long durationSeconds, boolean cached, String source) {

    public RouteResult asCached() {
        return new RouteResult(distanceMeters, durationSeconds, true, "CACHE");
    }
}
