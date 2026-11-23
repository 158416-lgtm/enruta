package com.enruta.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeoMathTest {

    @Test
    void samePointIsZero() {
        assertThat(GeoMath.distanceMeters(4.3449, -74.3674, 4.3449, -74.3674)).isZero();
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        double meters = GeoMath.distanceMeters(0, 0, 1, 0);
        assertThat(meters).isBetween(110_000.0, 112_000.0);
    }

    @Test
    void oneDegreeOfLongitudeShrinksWithLatitude() {
        double atEquator = GeoMath.distanceMeters(0, 0, 0, 1);
        double atFusagasuga = GeoMath.distanceMeters(4.3449, -74.3674, 4.3449, -73.3674);
        assertThat(atFusagasuga).isLessThan(atEquator);
        assertThat(atFusagasuga).isBetween(110_000.0, 112_000.0);
    }

    @Test
    void knownDistanceInFusagasuga() {
        // Parque Principal -> Terminal de Transportes (~1,1 km)
        double meters = GeoMath.distanceMeters(4.3449, -74.3674, 4.3374, -74.3600);
        assertThat(meters).isBetween(1_000.0, 1_300.0);
    }
}
