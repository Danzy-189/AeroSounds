package dev.danzy.aerosounds.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Sound needs time to travel. Every event is queued with the tick at which its
 * wavefront reaches the listener, which is what makes a distant boom arrive
 * seconds after the aircraft has already passed overhead.
 */
public final class SoundScheduler {

    private record Pending(long playTick, SoundEvent event, Vec3 pos, float volume, float pitch, int lifetime) {
    }

    private static final List<Pending> QUEUE = new ArrayList<>();

    public static void schedule(SoundEvent event, Vec3 pos, float volume, float pitch, int delayTicks, int lifetime) {
        if (volume <= 0.001f) {
            return;
        }
        QUEUE.add(new Pending(PhysicsClock.now() + Math.max(0, delayTicks), event, pos, volume, pitch, lifetime));
    }

    public static void tick() {
        if (QUEUE.isEmpty()) {
            return;
        }
        long now = PhysicsClock.now();
        Minecraft mc = Minecraft.getInstance();
        for (Iterator<Pending> it = QUEUE.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (now >= p.playTick()) {
                it.remove();
                if (mc.level != null) {
                    mc.getSoundManager().play(new BurstSoundInstance(p.event(), p.pos(), p.volume(), p.pitch(), p.lifetime()));
                }
            }
        }
    }

    public static void clear() {
        QUEUE.clear();
    }

    private SoundScheduler() {
    }
}
