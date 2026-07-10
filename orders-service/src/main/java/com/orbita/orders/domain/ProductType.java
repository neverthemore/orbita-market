package com.orbita.orders.domain;

/** Satellite product types supported by the platform. */
public enum ProductType {
    /** Purchase of an existing archived image. */
    ARCHIVE,
    /** Schedule a future satellite pass over an AOI. */
    TASKING,
    /** Recurring monitoring subscription for an AOI. */
    MONITORING
}
