package dev.danzy.aerosounds.client;

import java.util.Iterator;

import dev.danzy.aerosounds.ModSounds;
import dev.danzy.aerosounds.config.AeroConfig;
import dev.danzy.aerosounds.physics.PhysicsSource;
import dev.danzy.aerosounds.physics.PhysicsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Sonic booms.
 *
 * <p>A Mach cone is the envelope of the spherical waves an object emits while
 * flying faster than sound. Instead of solving the cone analytically the mod
 * keeps a short trail of emission points and asks a simple question every tick:
 * "has the wave emitted at tick T reached me?". The first such wave is the cone
 * itself, so the boom is heard exactly when the shock passes the listener -
 * long after the aircraft is overhead, and never for someone who is still
 * outside the cone.</p>
 */
public final class SonicBoomManager {

    public static void tick(Vec3 listener) {
        if (!AeroConfig.C.enabled.get() || AeroConfig.C.boomVolume.get() <= 0.0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        double c = AeroConfig.C.speedOfSound.get();
        double cPerTick = Acoustics.speedOfSoundPerTick();
        double maxDistance = AeroConfig.C.boomMaxDistance.get();
        long now = PhysicsClock.now();

        for (PhysicsSource source : PhysicsTracker.sources()) {
            source.recordShock(now, c);

            if (!source.boomArmed || now - source.lastBoomTick < AeroConfig.C.boomCooldownTicks.get()) {
                continue;
            }

            PhysicsSource.Shock arrived = null;
            for (Iterator<PhysicsSource.Shock> it = source.trail.iterator(); it.hasNext(); ) {
                PhysicsSource.Shock shock = it.next();
                if (!shock.supersonic()) {
                    continue;
                }
                double distance = shock.pos().distanceTo(listener);
                if (distance > maxDistance) {
                    continue;
                }
                long arrival = shock.tick() + Math.round(distance / cPerTick);
                if (now >= arrival) {
                    arrived = shock;
                    break; // the trail is ordered, the first hit is the cone
                }
            }

            if (arrived == null) {
                continue;
            }

            final long consumedUntil = arrived.tick();
            source.trail.removeIf(s -> s.tick() <= consumedUntil);
            source.lastBoomTick = now;
            // One cone, one boom: re-arm only after the object drops below Mach 1 again.
            source.boomArmed = false;

            Vec3 pos = arrived.pos();
            double distance = pos.distanceTo(listener);
            double mach = arrived.speed() / c;

            // Overpressure of an N-wave decays roughly with r^-3/4.
            double gain = AeroConfig.C.boomVolume.get()
                    * (0.65 + 0.45 * Math.min(2.0, mach - 1.0))
                    * Acoustics.spreading(distance, 24.0, 0.75)
                    * Acoustics.airAbsorption(distance)
                    * Acoustics.airDensity(pos.y)
                    * Acoustics.occlusion(mc.level, listener, pos)
                    * Acoustics.underwater()
                    * (1.0 - distance / maxDistance);

            boolean near = distance < 110.0;
            float pitch = (float) Acoustics.clamp(
                    (near ? 1.0 : 0.9) * Acoustics.distancePitch(distance)
                            * (0.95 + 0.1 * Math.min(1.0, mach - 1.0)),
                    0.5, 2.0);

            SoundScheduler.schedule(
                    near ? ModSounds.SONIC_BOOM_NEAR.get() : ModSounds.SONIC_BOOM_FAR.get(),
                    pos, Acoustics.proxyVolume(gain), pitch,
                    0, near ? 80 : 140);
        }
    }

    private SonicBoomManager() {
    }
}
