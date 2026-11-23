package com.enruta.dispatch.domain;

public enum OfferStatus {
    CREATED,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    SUPERSEDED;

    public boolean isPending() {
        return this == CREATED;
    }
}
