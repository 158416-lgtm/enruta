package com.enruta.dispatch.domain;

/** Disponibilidad del conductor. Solo AVAILABLE entra en el indice geografico de Redis. */
public enum DriverStatus {
    AVAILABLE,
    BUSY,
    OFFLINE
}
