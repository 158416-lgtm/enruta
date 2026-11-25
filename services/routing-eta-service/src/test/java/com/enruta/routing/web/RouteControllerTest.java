package com.enruta.routing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.enruta.routing.service.OsrmAdvancedClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RouteControllerTest {

    @Mock private OsrmAdvancedClient osrm;

    private RouteController controller;

    @BeforeEach
    void setUp() {
        controller = new RouteController(osrm);
    }

    @Test
    void routeReturnsDistanceDurationAndGeometry() {
        when(osrm.route(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(
                        new OsrmAdvancedClient.Route(
                                1200.5,
                                90.2,
                                List.of(new double[] {4.34, -74.36}, new double[] {4.35, -74.35})));

        RouteController.RouteResponse response = controller.route(4.34, -74.36, 4.35, -74.35);

        assertThat(response.distanceMeters()).isEqualTo(1200.5);
        assertThat(response.durationSeconds()).isEqualTo(90.2);
        assertThat(response.geometry()).hasSize(2);
    }

    @Test
    void distanceReturnsMeters() {
        when(osrm.distance(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(1200.5);

        assertThat(controller.distance(4.34, -74.36, 4.35, -74.35).distanceMeters())
                .isEqualTo(1200.5);
    }

    @Test
    void matrixConvertsPointsToCoordinatePairs() {
        when(osrm.matrix(anyList(), anyList()))
                .thenReturn(new double[][] {{0, 1500.2}, {1800.4, 0}});

        RouteController.MatrixResponse response =
                controller.matrix(
                        new RouteController.MatrixRequest(
                                List.of(new RouteController.Point(4.34, -74.36)),
                                List.of(
                                        new RouteController.Point(4.35, -74.35),
                                        new RouteController.Point(4.36, -74.34))));

        assertThat(response.distancesMeters()).hasDimensions(2, 2);
        assertThat(response.distancesMeters()[0][1]).isEqualTo(1500.2);
    }

    @Test
    void nearestReturnsSnappedPoint() {
        when(osrm.nearest(anyDouble(), anyDouble())).thenReturn(new double[] {4.34, -74.36});

        RouteController.NearestResponse response = controller.nearest(4.341, -74.361);

        assertThat(response.lat()).isEqualTo(4.34);
        assertThat(response.lng()).isEqualTo(-74.36);
    }
}
