package com.enruta.contracts;

/** Nombres de topicos Kafka. Unico lugar donde se declaran. */
public final class Topics {

    public static final String SHIPMENT_EVENTS = "shipment.events";
    public static final String DISPATCH_EVENTS = "dispatch.events";
    public static final String TRACKING_GPS_RAW = "tracking.gps.raw";
    public static final String TRACKING_EVENTS = "tracking.events";
    public static final String NOTIFICATION_COMMANDS = "notification.commands";

    /** Sufijo para mensajes que agotaron sus reintentos. */
    public static final String DLQ_SUFFIX = ".dlq";

    public static String dlqOf(String topic) {
        return topic + DLQ_SUFFIX;
    }

    private Topics() {}
}
