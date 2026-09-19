package dev.danzy.aerosounds.client;

import dev.danzy.aerosounds.config.AeroConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The acoustic model of the mod.
 *
 * <p>Minecraft couples loudness and audible range into a single "volume"
 * parameter, so a distant loud event cannot be expressed directly. Aero Sounds
 * therefore computes the perceived gain itself (geometric spreading, air
 * absorption, occlusion, air density) and plays the sample on a short "proxy"
 * position a couple of blocks from the listener in the true direction of the
 * source. Direction and panning stay correct, loudness becomes ours.</p>
 */
public final class Acoustics {

    /** Distance at which proxy sounds are placed, blocks. */
    public static final double PROXY_DISTANCE = 2.0;
    /** Compensation for the linear falloff Minecraft applies over those 2 blocks. */
    private static final double PROXY_COMPENSATION = 1.15;

    /** Speed of sound in blocks per tick. */
    public static double speedOfSoundPerTick() {
        return AeroConfig.C.speedOfSound.get() / 20.0;
    }

    /** Ticks a sound needs to travel the given distance (0 when delay is disabled). */
    public static int travelTicks(double distance) {
        if (!AeroConfig.C.propagationDelay.get()) {
            return 0;
        }
        return (int) Math.round(distance / speedOfSoundPerTick());
    }

    /**
     * Geometric spreading. Real point sources fall off with 1/r; extended, moving
     * sources such as an aircraft behave softer, and a shock wave decays roughly
     * with r^-0.75, hence the configurable exponent.
     */
    public static double spreading(double distance, double referenceDistance, double exponent) {
        double d = Math.max(distance, referenceDistance);
        return Math.pow(referenceDistance / d, exponent);
    }

    /** Molecular absorption of air: distant sounds lose their highs and their energy. */
    public static double airAbsorption(double distance) {
        if (!AeroConfig.C.airAbsorption.get()) {
            return 1.0;
        }
        return Math.exp(-distance / 340.0);
    }

    /** Dull, low-passed character of far away sounds, expressed as a pitch drop. */
    public static float distancePitch(double distance) {
        if (!AeroConfig.C.airAbsorption.get()) {
            return 1.0f;
        }
        return (float) Math.max(0.72, 1.0 - distance / 1200.0);
    }

    /** Thin air high in the sky carries less aerodynamic noise. */
    public static double airDensity(double y) {
        if (!AeroConfig.C.altitudeDensity.get()) {
            return 1.0;
        }
        double above = y - 160.0;
        if (above <= 0) {
            return 1.0;
        }
        return Math.max(0.35, Math.exp(-above / 420.0));
    }

    /** Cheap line-of-sight occlusion: each blocking hit costs a share of the volume. */
    public static double occlusion(Level level, Vec3 listener, Vec3 source) {
        if (!AeroConfig.C.occlusion.get() || level == null) {
            return 1.0;
        }
        BlockHitResult hit = level.clip(new ClipContext(listener, source,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) {
            return 1.0;
        }
        double strength = AeroConfig.C.occlusionStrength.get();
        // A second probe from the source side tells blocked-by-a-wall from
        // blocked-by-a-fence-post apart.
        BlockHitResult back = level.clip(new ClipContext(source, listener,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        double thickness = back.getType() == HitResult.Type.MISS
                ? 0.5
                : Math.min(1.0, hit.getLocation().distanceTo(back.getLocation()) / 6.0 + 0.4);
        return Math.max(0.05, 1.0 - strength * thickness);
    }

    /** Muffling while the listener is submerged. */
    public static double underwater() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isUnderWater() ? 0.45 : 1.0;
    }

    /**
     * Doppler ratio f' / f for a source and a listener moving in world space.
     * Velocities are in blocks per tick.
     */
    public static float doppler(Vec3 listenerPos, Vec3 listenerVel, Vec3 sourcePos, Vec3 sourceVel) {
        if (!AeroConfig.C.doppler.get()) {
            return 1.0f;
        }
        Vec3 toListener = listenerPos.subtract(sourcePos);
        double dist = toListener.length();
        if (dist < 1.0e-3) {
            return 1.0f;
        }
        Vec3 dir = toListener.scale(1.0 / dist);
        double c = speedOfSoundPerTick();
        // Positive when moving towards each other.
        double vSource = sourceVel.dot(dir);
        double vListener = -listenerVel.dot(dir);
        double denom = c - vSource;
        if (denom < c * 0.15) {
            denom = c * 0.15; // keep the ratio finite through Mach 1
        }
        double ratio = (c + vListener) / denom;
        double strength = AeroConfig.C.dopplerStrength.get();
        ratio = 1.0 + (ratio - 1.0) * strength;
        return (float) clamp(ratio, 0.5, 2.0);
    }

    /** Position used to actually play the sample: near the listener, true direction. */
    public static Vec3 proxyPos(Vec3 listener, Vec3 source) {
        Vec3 delta = source.subtract(listener);
        double len = delta.length();
        if (len < 1.0e-4) {
            return listener;
        }
        return listener.add(delta.scale(PROXY_DISTANCE / len));
    }

    public static float proxyVolume(double gain) {
        return (float) clamp(gain * PROXY_COMPENSATION * AeroConfig.C.masterVolume.get(), 0.0, 1.0);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    private Acoustics() {
    }
}
