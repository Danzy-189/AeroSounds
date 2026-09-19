package dev.danzy.aerosounds.client;

import dev.danzy.aerosounds.ModSounds;
import dev.danzy.aerosounds.config.AeroConfig;
import dev.danzy.aerosounds.physics.PhysicsSource;
import dev.danzy.aerosounds.physics.PhysicsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * A looping layer of aerodynamic noise attached to one physics object.
 *
 * <p>Three layers are cross-faded by speed: an airy rush, a heavy low roar and
 * the transonic buffet just below Mach 1. Volume, pitch and stereo position are
 * recomputed every client tick from the object's kinematics.</p>
 */
public class TrackedLoopInstance extends AbstractTickableSoundInstance {

    public enum Layer {
        LIGHT,
        HEAVY,
        TRANSONIC
    }

    private final int entityId;
    private final Layer layer;

    private float smoothedVolume;
    private float smoothedPitch = 1.0f;
    private double cachedOcclusion = 1.0;
    private int occlusionTimer;

    public TrackedLoopInstance(int entityId, Layer layer) {
        super(switch (layer) {
            case LIGHT -> ModSounds.WIND_LIGHT.get();
            case HEAVY -> ModSounds.WIND_HEAVY.get();
            case TRANSONIC -> ModSounds.TRANSONIC_RUMBLE.get();
        }, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.entityId = entityId;
        this.layer = layer;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.pitch = 1.0f;
        this.attenuation = Attenuation.LINEAR;
        Vec3 l = ClientEvents.listenerPos();
        this.x = l.x;
        this.y = l.y;
        this.z = l.z;
    }

    public int entityId() {
        return entityId;
    }

    public Layer layer() {
        return layer;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        PhysicsSource source = PhysicsTracker.get(entityId);
        if (mc.level == null || source == null || !source.entity.isAlive() || !AeroConfig.C.enabled.get()) {
            fadeOut();
            return;
        }

        Vec3 listener = ClientEvents.listenerPos();
        Vec3 sourcePos = source.soundPos();
        double distance = listener.distanceTo(sourcePos);
        double maxDistance = AeroConfig.C.windMaxDistance.get();
        if (distance > maxDistance) {
            fadeOut();
            return;
        }

        // The player riding this very contraption hears the cabin loop instead.
        if (mc.player != null && mc.player.getRootVehicle() == source.entity) {
            fadeOut();
            return;
        }

        if (--occlusionTimer <= 0) {
            occlusionTimer = 5;
            cachedOcclusion = Acoustics.occlusion(mc.level, listener, sourcePos);
        }

        double minSpeed = AeroConfig.C.windMinSpeed.get();
        double fullSpeed = AeroConfig.C.windFullSpeed.get();
        double speed = source.speed();
        double speedFactor = Acoustics.clamp((speed - minSpeed) / Math.max(1.0, fullSpeed - minSpeed), 0.0, 1.0);
        double mach = speed / AeroConfig.C.speedOfSound.get();

        double layerWeight = switch (layer) {
            case LIGHT -> (1.0 - 0.75 * speedFactor) * Math.min(1.0, speedFactor * 4.0);
            case HEAVY -> Math.pow(speedFactor, 1.35);
            case TRANSONIC -> AeroConfig.C.transonicRumble.get()
                    ? bell(mach, 0.85, 1.0, 1.25)
                    : 0.0;
        };

        double sizeGain = Acoustics.clamp(Math.sqrt(source.frontalArea) / 4.0, 0.3, 1.6);
        double loudness = layerWeight * sizeGain * AeroConfig.C.windVolume.get();

        double gain = loudness
                * Acoustics.spreading(distance, 8.0, 1.0)
                * Acoustics.airAbsorption(distance)
                * Acoustics.airDensity(sourcePos.y)
                * cachedOcclusion
                * Acoustics.underwater()
                * (1.0 - distance / maxDistance); // clean fade at the audible edge

        float targetVolume = Acoustics.proxyVolume(Math.max(0.0, gain));
        float basePitch = switch (layer) {
            case LIGHT -> (float) (0.85 + 0.5 * speedFactor);
            case HEAVY -> (float) (0.72 + 0.4 * speedFactor);
            case TRANSONIC -> (float) (0.9 + 0.2 * (mach - 0.85) / 0.15);
        };
        float targetPitch = (float) Acoustics.clamp(
                basePitch
                        * Acoustics.doppler(listener, ClientEvents.listenerVelocity(), sourcePos, source.velocity)
                        * Acoustics.distancePitch(distance),
                0.5, 2.0);

        smoothedVolume = smoothedVolume + (targetVolume - smoothedVolume) * 0.25f;
        smoothedPitch = smoothedPitch + (targetPitch - smoothedPitch) * 0.35f;
        this.volume = smoothedVolume;
        this.pitch = smoothedPitch;

        Vec3 proxy = Acoustics.proxyPos(listener, sourcePos);
        this.x = proxy.x;
        this.y = proxy.y;
        this.z = proxy.z;

        if (smoothedVolume < 0.0015f && targetVolume <= 0.0f) {
            stop();
        }
    }

    private void fadeOut() {
        smoothedVolume *= 0.6f;
        this.volume = smoothedVolume;
        if (smoothedVolume < 0.002f) {
            stop();
        }
    }

    /** Triangular window used for the transonic buffet. */
    private static double bell(double value, double start, double peak, double end) {
        if (value <= start || value >= end) {
            return 0.0;
        }
        return value < peak
                ? (value - start) / (peak - start)
                : 1.0 - (value - peak) / (end - peak);
    }
}
