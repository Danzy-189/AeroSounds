package dev.danzy.aerosounds.client;

import dev.danzy.aerosounds.ModSounds;
import dev.danzy.aerosounds.config.AeroConfig;
import dev.danzy.aerosounds.physics.PhysicsSource;
import dev.danzy.aerosounds.physics.PhysicsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Wind heard from inside: when the player rides a physics contraption the noise
 * comes from everywhere at once, scales with the vehicle's own speed and is
 * damped when the player sits in an enclosed cabin.
 */
public final class CabinWindManager {

    private static CabinLoop loop;

    public static void tick() {
        if (loop != null && loop.isStopped()) {
            loop = null;
        }
        if (!AeroConfig.C.enabled.get() || AeroConfig.C.cabinWindVolume.get() <= 0.0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Entity vehicle = mc.player.getRootVehicle();
        if (vehicle == mc.player || !PhysicsTracker.isPhysicsObject(vehicle)) {
            return;
        }
        if (loop == null) {
            loop = new CabinLoop(vehicle.getId());
            mc.getSoundManager().play(loop);
        }
    }

    public static void clear() {
        if (loop != null) {
            loop.stopNow();
            loop = null;
        }
    }

    private static class CabinLoop extends AbstractTickableSoundInstance {

        private final int vehicleId;
        private float smoothed;

        CabinLoop(int vehicleId) {
            super(ModSounds.WIND_CABIN.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.vehicleId = vehicleId;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001f;
            this.relative = true; // rides with the listener's head
            this.attenuation = Attenuation.NONE;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }

        /** Public wrapper: {@link #stop()} is protected in the vanilla base class. */
        public void stopNow() {
            stop();
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            PhysicsSource source = PhysicsTracker.get(vehicleId);
            if (mc.player == null || source == null || mc.player.getRootVehicle() != source.entity) {
                smoothed *= 0.7f;
                this.volume = smoothed;
                if (smoothed < 0.002f) {
                    stop();
                }
                return;
            }

            double speed = source.speed();
            double full = AeroConfig.C.windFullSpeed.get();
            double factor = Acoustics.clamp((speed - 2.0) / Math.max(1.0, full - 2.0), 0.0, 1.4);
            // Sitting inside a closed hull is much quieter than an open cockpit.
            double enclosure = mc.player.level().canSeeSky(mc.player.blockPosition()) ? 1.0 : 0.55;

            double gain = Math.pow(factor, 1.3)
                    * AeroConfig.C.cabinWindVolume.get()
                    * enclosure
                    * Acoustics.airDensity(source.soundPos().y)
                    * Acoustics.underwater();

            float target = (float) Acoustics.clamp(gain * AeroConfig.C.masterVolume.get(), 0.0, 1.0);
            smoothed = smoothed + (target - smoothed) * 0.2f;
            this.volume = smoothed;
            this.pitch = (float) Acoustics.clamp(0.8 + 0.5 * factor, 0.5, 2.0);
        }
    }

    private CabinWindManager() {
    }
}
