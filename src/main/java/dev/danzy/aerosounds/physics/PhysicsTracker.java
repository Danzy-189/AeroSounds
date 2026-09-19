package dev.danzy.aerosounds.physics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.danzy.aerosounds.config.AeroConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;

/**
 * Finds every moving physics contraption near the player and keeps a smoothed
 * kinematic state for it.
 *
 * <p>Detection is deliberately API-free: an entity qualifies when its registry
 * namespace belongs to a physics mod (configurable) or when its class name
 * looks like a contraption. This keeps the addon working across Create:
 * Aeronautics updates, Sable versions and sibling mods (Simulated, Offroad).</p>
 */
public final class PhysicsTracker {

    private static final Map<Integer, PhysicsSource> SOURCES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> CLASS_CACHE = new ConcurrentHashMap<>();

    private static long tick;

    public static long currentTick() {
        return tick;
    }

    public static void clear() {
        SOURCES.clear();
        CLASS_CACHE.clear();
    }

    public static List<PhysicsSource> sources() {
        return new ArrayList<>(SOURCES.values());
    }

    public static PhysicsSource get(int entityId) {
        return SOURCES.get(entityId);
    }

    public static void tick(ClientLevel level, Vec3 listener) {
        tick++;

        double radius = Math.max(AeroConfig.C.windMaxDistance.get(), AeroConfig.C.boomMaxDistance.get()) + 64.0;
        double radiusSqr = radius * radius;
        Set<Integer> seen = new HashSet<>();

        int limit = AeroConfig.C.maxTrackedSources.get();
        for (Entity entity : level.entitiesForRendering()) {
            if (seen.size() >= limit) {
                break;
            }
            if (!entity.isAlive() || !isPhysicsObject(entity)) {
                continue;
            }
            if (entity.position().distanceToSqr(listener) > radiusSqr) {
                continue;
            }
            PhysicsSource source = SOURCES.get(entity.getId());
            if (source == null) {
                source = new PhysicsSource(entity, tick);
                SOURCES.put(entity.getId(), source);
            } else {
                source.update(tick);
            }
            if (source.size >= AeroConfig.C.minSize.get()) {
                seen.add(entity.getId());
            }
        }

        for (Iterator<Map.Entry<Integer, PhysicsSource>> it = SOURCES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, PhysicsSource> e = it.next();
            PhysicsSource s = e.getValue();
            if (!s.entity.isAlive() || tick - s.lastSeenTick > 20) {
                it.remove();
            }
        }
    }

    public static boolean isPhysicsObject(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (AeroConfig.C.includeVanillaVehicles.get() && (entity instanceof AbstractMinecart || entity instanceof Boat)) {
            return true;
        }
        Boolean cached = CLASS_CACHE.get(entity.getClass());
        if (cached != null) {
            return cached;
        }
        boolean result = evaluate(entity);
        CLASS_CACHE.put(entity.getClass(), result);
        return result;
    }

    private static boolean evaluate(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id != null && AeroConfig.split(AeroConfig.C.namespaces.get()).contains(id.getNamespace().toLowerCase())) {
            return true;
        }
        String className = entity.getClass().getName().toLowerCase();
        String path = id == null ? "" : id.getPath().toLowerCase();
        for (String hint : AeroConfig.split(AeroConfig.C.classNameHints.get())) {
            if (className.contains(hint) || path.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private PhysicsTracker() {
    }
}
