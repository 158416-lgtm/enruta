package com.enruta.dispatch.seed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enruta.dispatch.domain.Driver;
import com.enruta.dispatch.domain.DriverStatus;
import com.enruta.dispatch.repository.DriverRepository;
import com.enruta.dispatch.service.CandidateFinder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class DriverSeederTest {

    @Mock private DriverRepository drivers;
    @Mock private CandidateFinder finder;

    @Test
    void seedsMissingDriversAndRebuildsTheGeoIndex() {
        List<Driver> available =
                List.of(
                        new Driver(
                                DriverSeeder.DRIVER_IDS.get(0),
                                "Ana",
                                "MOTO",
                                4.9,
                                4.3449,
                                -74.3674),
                        new Driver(
                                DriverSeeder.DRIVER_IDS.get(1),
                                "Beto",
                                "MOTO",
                                4.7,
                                4.3400,
                                -74.3620));
        when(drivers.findByStatus(DriverStatus.AVAILABLE)).thenReturn(available);

        new DriverSeeder(drivers, finder, true).run(new DefaultApplicationArguments());

        verify(drivers, times(DriverSeeder.DRIVER_IDS.size())).save(any(Driver.class));
        verify(finder).index(DriverSeeder.DRIVER_IDS.get(0), 4.3449, -74.3674);
        verify(finder).index(DriverSeeder.DRIVER_IDS.get(1), 4.3400, -74.3620);
    }

    @Test
    void existingDriversAreNotSeededTwice() {
        when(drivers.existsById(DriverSeeder.DRIVER_IDS.get(0))).thenReturn(true);
        when(drivers.findByStatus(DriverStatus.AVAILABLE)).thenReturn(List.of());

        new DriverSeeder(drivers, finder, true).run(new DefaultApplicationArguments());

        verify(drivers, times(DriverSeeder.DRIVER_IDS.size() - 1)).save(any(Driver.class));
        verify(drivers, never())
                .save(
                        org.mockito.ArgumentMatchers.argThat(
                                driver -> driver.getId().equals(DriverSeeder.DRIVER_IDS.get(0))));
        verify(finder, never()).index(any(), eq(4.3449), eq(-74.3674));
    }

    @Test
    void seedingCanBeDisabled() {
        new DriverSeeder(drivers, finder, false).run(new DefaultApplicationArguments());

        verifyNoInteractions(drivers, finder);
    }

    @Test
    void availableDriversWithoutAPositionAreNotIndexed() {
        Driver withoutPosition = org.mockito.Mockito.mock(Driver.class);
        when(withoutPosition.getCurrentLat()).thenReturn(4.3449);
        when(withoutPosition.getCurrentLng()).thenReturn(null);
        when(drivers.findByStatus(DriverStatus.AVAILABLE)).thenReturn(List.of(withoutPosition));

        new DriverSeeder(drivers, finder, true).run(new DefaultApplicationArguments());

        verify(finder, never()).index(any(), anyDouble(), anyDouble());
    }
}
