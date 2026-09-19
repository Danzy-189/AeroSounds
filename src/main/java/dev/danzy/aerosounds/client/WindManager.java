package dev.danzy.aerosounds.client;

import java.util.HashMap;
import java.util.Map;

import dev.danzy.aerosounds.config.AeroConfig;
import dev.danzy.aerosounds.physics.PhysicsSource;
import dev.danzy.aerosounds.physics.PhysicsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Keeps exactly one looping layer set alive per audible moving contraption. */
public final class WindManager {

    private record Key(int entityId, TrackedLoopInstance.Layer layer) {
    }

    private static final Map<Key, TrackedLoopInstance> ACTIVE = new HashMap<>();

    public static void tick(Vec3 listener) {
        Minecraft mc = Minecraft.getInstance();
        ACTIVE.entrySet().removeIf(e -> e.getValue().isStopped());

        if (!AeroConfig.C.enabled.get() || AeroConfig.C.windVolume.get() <= 0.0) {
            return;
        }

        double maxDistance = AeroConfig.C.windMaxDistance.get();
        double minSpeed = AeroConfig.C.windMinSpeed.get();

        for (PhysicsSource source : PhysicsTracker.sources()) {
            if (source.size < AeroConfig.C.minSize.get()) {
                continue;
            }
            double speed = source.speed();
            if (speed < minSpeed) {
                continue;
            }
            if (source.soundPos().distanceTo(listener) > maxDistance) {
                continue;
            }
            if (mc.player != null && mc.player.getRootVehicle() == source.entity) {
                continue; // handled by the cabin loop
            }

            start(source, TrackedLoopInstance.Layer.LIGHT);
            start(source, TrackedLoopInstance.Layer.HEAVY);
            if (AeroConfig.C.transonicRumble.get()
                    && speed > 0.8 * AeroConfig.C.speedOfSound.get()) {
                start(source, TrackedLoopInstance.Layer.TRANSONIC);
            }
        }
    }

    private static void start(PhysicsSource source, TrackedLoopInstance.Layer layer) {
        Key key = new Key(source.entityId, layer);
        if (ACTIVE.containsKey(key)) {
            return;
        }
        TrackedLoopInstance instance = new TrackedLoopInstance(source.entityId, layer);
        ACTIVE.put(key, instance);
        Minecraft.getInstance().getSoundManager().play(instance);
    }

    public static void clear() {
        ACTIVE.values().forEach(TrackedLoopInstance::stopNow);
        ACTIVE.clear();
    }

    private WindManager() {
    }
}
