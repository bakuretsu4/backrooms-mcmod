package com.rickr.backrooms.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** New arrivals in Level 39 get exactly one minute before anything may spawn near them. */
class Level39ArrivalTest {
    @Test
    void graceLastsOneMinute() {
        long arrived = 5000;
        assertTrue(Level39Arrival.withinGrace(arrived, arrived));
        assertTrue(Level39Arrival.withinGrace(arrived, arrived + Level39Arrival.ARRIVAL_GRACE_TICKS - 1));
        assertFalse(Level39Arrival.withinGrace(arrived, arrived + Level39Arrival.ARRIVAL_GRACE_TICKS));
        assertFalse(Level39Arrival.withinGrace(arrived, arrived + 100_000));
    }
}
