package dev.danzy.aerosounds.client;

import dev.danzy.aerosounds.ModSounds;
import dev.danzy.aerosounds.config.AeroConfig;
import dev.danzy.aerosounds.physics.PhysicsSource;
import dev.danzy.aerosounds.physics.PhysicsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * The "vzhuh": a single swoosh fired at the moment of closest approach.
 *
 * <p>The event is detected as a local minimum of the distance to the listener
 * and then delayed by the travel time of sound, so a car that shoots past you
 * is heard exactly when its pressure wave arrives, with the full Doppler drop
 * baked into the pitch envelope of the sample.</p>
 */
public final class PassByManager {

    public static void tick(Vec3 listener) {
        if (!AeroConfig.C.enabled.get() || AeroConfig.C.whooshVolume.get() <= 0.0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        double maxMiss = AeroConfig.C.whooshMaxMissDistance.get();
        double minSpeed = AeroConfig.C.whooshMinSpeed.get();
        long now = PhysicsClock.now();

        for (PhysicsSource source : PhysicsTracker.sources()) {
            if (source.size < AeroConfig.C.minSize.get()) {
                continue;
            }
            if (mc.player != null && mc.player.getRootVehicle() == source.entity) {
                source.previousDistanceToListener = Double.MAX_VALUE;
                continue;
            }

            Vec3 pos = source.soundPos();
            double distance = pos.distanceTo(listener);
            double previous = source.previousDistanceToListener;
            source.previousDistanceToListener = distance;

            double speed = source.speed();
            if (speed < minSpeed || distance > maxMiss || previous == Double.MAX_VALUE) {
                continue;
            }
            // Closest approach: the object was getting closer and now moves away.
            boolean passed = distance > previous;
            if (!passed) {
                continue;
            }
            if (now - source.lastWhooshTick < 20) {
                continue;
            }
            source.lastWhooshTick = now;

            double speedFactor = Acoustics.clamp(speed / Math.max(20.0, AeroConfig.C.windFullSpeed.get()), 0.15, 1.6);
            double missFactor = 1.0 - Math.min(1.0, distance / maxMiss);
            double sizeGain = Acoustics.clamp(Math.sqrt(source.frontalArea) / 4.0, 0.3, 1.6);

            double gain = AeroConfig.C.whooshVolume.get()
                    * speedFactor * Math.pow(missFactor, 0.8) * sizeGain
                    * Acoustics.occlusion(mc.level, listener, pos)
                    * Acoustics.airDensity(pos.y)
                    * Acoustics.underwater();

            float pitch = (float) Acoustics.clamp(0.8 + 0.6 * speedFactor, 0.5, 2.0);
            SoundScheduler.schedule(ModSounds.WHOOSH_PASS.get(), pos,
                    Acoustics.proxyVolume(gain), pitch,
                    Acoustics.travelTicks(distance), 40);
        }
    }

    private PassByManager() {
    }
}
