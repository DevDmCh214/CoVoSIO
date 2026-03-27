package com.covosio.entity;

/** Lifecycle states of a passenger reservation. */
public enum ReservationStatus {
    /** Created, awaiting driver action or automatic confirmation. */
    PENDING,
    /** Driver confirmed or automatically confirmed. */
    CONFIRMED,
    /** Cancelled by passenger or by cascade when the trip was cancelled. */
    CANCELLED
}
