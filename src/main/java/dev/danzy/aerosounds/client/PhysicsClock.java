package dev.danzy.aerosounds.client;

import dev.danzy.aerosounds.physics.PhysicsTracker;

/** Single monotonic client tick counter shared by every subsystem. */
public final class PhysicsClock {

    public static long now() {
        return PhysicsTracker.currentTick();
    }

    private PhysicsClock() {
    }
}
