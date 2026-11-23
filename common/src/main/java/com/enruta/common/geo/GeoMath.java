package com.enruta.common.geo;

/** Utilidades geodesicas puras, sin dependencias de framework. */
public final class GeoMath {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    /** Distancia haversine en metros. */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                                * Math.cos(Math.toRadians(lat2))
                                * Math.sin(dLng / 2)
                                * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /**
     * Estimacion de duracion cuando no hay motor de rutas.
     *
     * <p>Se aplica un factor de 1.3 sobre la distancia en linea recta para aproximar el rodeo de
     * las calles. No sustituye a OSRM: es el plan B para no dejar al cliente sin respuesta.
     */
    public static long estimatedDurationSeconds(
            double straightDistanceMeters, double averageSpeedKph) {
        if (averageSpeedKph <= 0) {
            throw new IllegalArgumentException("La velocidad media debe ser positiva");
        }
        double roadDistance = straightDistanceMeters * 1.3;
        return Math.round(roadDistance / (averageSpeedKph * 1000.0 / 3600.0));
    }

    private GeoMath() {}
}
