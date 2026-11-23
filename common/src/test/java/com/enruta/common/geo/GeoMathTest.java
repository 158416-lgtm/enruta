package com.enruta.common.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GeoMathTest {

    @Test
    void samePointIsZero() {
        assertThat(GeoMath.distanceMeters(4.3449, -74.3674, 4.3449, -74.3674)).isZero();
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        assertThat(GeoMath.distanceMeters(0, 0, 1, 0)).isBetween(110_000.0, 112_000.0);
    }

    @Test
    void longitudeShrinksWithLatitude() {
        double equator = GeoMath.distanceMeters(0, 0, 0, 1);
        double fusagasuga = GeoMath.distanceMeters(4.3449, -74.3674, 4.3449, -73.3674);
        assertThat(fusagasuga).isLessThan(equator);
    }

    @Test
    void knownDistanceInFusagasuga() {
        // Parque Principal -> Terminal de Transportes (~1,1 km)
        double meters = GeoMath.distanceMeters(4.3449, -74.3674, 4.3374, -74.3600);
        assertThat(meters).isBetween(1_000.0, 1_300.0);
    }

    @Test
    void fallbackDurationGrowsWithDistanceAndShrinksWithSpeed() {
        long slow = GeoMath.estimatedDurationSeconds(1000, 20);
        long fast = GeoMath.estimatedDurationSeconds(1000, 40);
        assertThat(slow).isGreaterThan(fast);

        long shortTrip = GeoMath.estimatedDurationSeconds(500, 25);
        long longTrip = GeoMath.estimatedDurationSeconds(5000, 25);
        assertThat(longTrip).isGreaterThan(shortTrip);
    }

    @Test
    void fallbackRejectsInvalidSpeed() {
        assertThatThrownBy(() -> GeoMath.estimatedDurationSeconds(100, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
