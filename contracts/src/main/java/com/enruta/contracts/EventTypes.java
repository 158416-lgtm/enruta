package com.enruta.contracts;

/** Tipos de evento. El valor viaja en {@code EventEnvelope.eventType}. */
public final class EventTypes {

    // shipment.events
    public static final String SHIPMENT_CREATED = "shipment.created";
    public static final String SHIPMENT_ASSIGNED = "shipment.assigned";
    public static final String SHIPMENT_UNASSIGNED = "shipment.unassigned";
    public static final String SHIPMENT_STATUS_CHANGED = "shipment.status-changed";
    public static final String SHIPMENT_DELIVERED = "shipment.delivered";
    public static final String SHIPMENT_FAILED = "shipment.failed";
    public static final String SHIPMENT_CANCELLED = "shipment.cancelled";
    public static final String SHIPMENT_NO_DRIVER = "shipment.no-driver";

    // dispatch.events
    public static final String DISPATCH_OFFER_CREATED = "dispatch.offer-created";
    public static final String DISPATCH_ASSIGNED = "dispatch.assigned";
    public static final String DISPATCH_ASSIGNMENT_CANCELLED = "dispatch.assignment-cancelled";
    public static final String DISPATCH_FAILED = "dispatch.failed";

    // tracking.events
    public static final String TRACKING_POSITION_UPDATED = "tracking.position-updated";
    public static final String TRACKING_GEOFENCE_ENTERED = "tracking.geofence-entered";
    public static final String TRACKING_GEOFENCE_EXITED = "tracking.geofence-exited";
    public static final String TRACKING_TRIP_STARTED = "tracking.trip-started";

    // notification.commands
    public static final String NOTIFICATION_REQUESTED = "notification.requested";

    private EventTypes() {}
}
