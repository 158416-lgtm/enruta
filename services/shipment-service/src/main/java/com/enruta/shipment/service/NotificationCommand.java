package com.enruta.shipment.service;

import java.util.Map;
import java.util.UUID;

/** Comando que shipment envia a notification. Notification decide como entregarlo. */
public record NotificationCommand(
        UUID shipmentId,
        String recipient,
        String channel,
        String template,
        Map<String, Object> variables,
        String dedupeKey) {}
