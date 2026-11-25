package com.enruta.notification.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class RetrySchedulerTest {

    @Test
    void retryPendingDelegatesToTheService() {
        NotificationService notifications = mock(NotificationService.class);
        when(notifications.processDue()).thenReturn(2);
        RetryScheduler scheduler = new RetryScheduler(notifications);

        scheduler.retryPending();

        verify(notifications).processDue();
    }

    @Test
    void retryPendingWorksWhenThereIsNothingToDo() {
        NotificationService notifications = mock(NotificationService.class);
        when(notifications.processDue()).thenReturn(0);
        RetryScheduler scheduler = new RetryScheduler(notifications);

        scheduler.retryPending();

        verify(notifications).processDue();
    }
}
