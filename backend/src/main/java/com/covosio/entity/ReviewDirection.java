package com.covosio.entity;

/** Direction of a post-trip review. Enforces R05: one review per reservation per direction. */
public enum ReviewDirection {
    /** Passenger reviews the driver (UC-P06). */
    PASSENGER_TO_DRIVER,
    /** Driver reviews the passenger (UC-D09). */
    DRIVER_TO_PASSENGER
}
