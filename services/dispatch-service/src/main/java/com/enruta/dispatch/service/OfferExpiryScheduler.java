package com.enruta.dispatch.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tick que vence ofertas y avanza al siguiente candidato. */
@Component
public class OfferExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(OfferExpiryScheduler.class);

    private final DispatchService dispatchService;

    public OfferExpiryScheduler(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(
            fixedDelayString = "${enruta.dispatch.expiry-poll-ms:1000}",
            initialDelayString = "${enruta.dispatch.expiry-initial-delay-ms:3000}")
    @SchedulerLock(name = "offer-expiry", lockAtMostFor = "PT20S", lockAtLeastFor = "PT0S")
    public void expireOffers() {
        int expired = dispatchService.expireDueOffers();
        if (expired > 0) {
            log.info("ofertas vencidas procesadas={}", expired);
        }
    }
}
