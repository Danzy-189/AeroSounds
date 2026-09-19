package dev.danzy.aerosounds.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * One-shot sound anchored to a world position but played through a proxy point
 * near the listener, so that its loudness follows the mod's own acoustic model
 * while panning still points at the real event.
 */
public class BurstSoundInstance extends AbstractTickableSoundInstance {

    private final Vec3 worldPos;
    private int lifetime;

    public BurstSoundInstance(SoundEvent event, Vec3 worldPos, float volume, float pitch, int lifetimeTicks) {
        super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.worldPos = worldPos;
        this.volume = volume;
        this.pitch = pitch;
        this.looping = false;
        this.delay = 0;
        this.lifetime = lifetimeTicks;
        this.attenuation = Attenuation.LINEAR;
        Vec3 proxy = Acoustics.proxyPos(ClientEvents.listenerPos(), worldPos);
        this.x = proxy.x;
        this.y = proxy.y;
        this.z = proxy.z;
    }

    @Override
    public void tick() {
        if (--lifetime <= 0) {
            stop();
            return;
        }
        Vec3 proxy = Acoustics.proxyPos(ClientEvents.listenerPos(), worldPos);
        this.x = proxy.x;
        this.y = proxy.y;
        this.z = proxy.z;
    }
}
