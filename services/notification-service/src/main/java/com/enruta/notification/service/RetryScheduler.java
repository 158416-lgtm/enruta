package com.enruta.notification.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tick que reintenta las notificaciones vencidas. */
@Component
public class RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetryScheduler.class);

    private final NotificationService notifications;

    public RetryScheduler(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Scheduled(
            fixedDelayString = "${enruta.notification.retry-poll-ms:1000}",
            initialDelayString = "${enruta.notification.retry-initial-delay-ms:3000}")
    @SchedulerLock(name = "notification-retry", lockAtMostFor = "PT20S", lockAtLeastFor = "PT0S")
    public void retryPending() {
        int processed = notifications.processDue();
        if (processed > 0) {
            log.info("reintentos procesados={}", processed);
        }
    }
}
