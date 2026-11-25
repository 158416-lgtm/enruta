package com.enruta.dispatch.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfferExpirySchedulerTest {

    @Mock private DispatchService dispatchService;

    @Test
    void expiredOffersAreReported() {
        when(dispatchService.expireDueOffers()).thenReturn(3);

        new OfferExpiryScheduler(dispatchService).expireOffers();

        verify(dispatchService).expireDueOffers();
    }

    @Test
    void nothingIsReportedWhenNoOfferExpires() {
        when(dispatchService.expireDueOffers()).thenReturn(0);

        new OfferExpiryScheduler(dispatchService).expireOffers();

        verify(dispatchService).expireDueOffers();
    }
}
