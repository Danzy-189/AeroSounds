package dev.danzy.aerosounds.physics;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A moving object that is able to produce aerodynamic noise.
 *
 * <p>Everything is derived from what the client can observe - per tick position
 * deltas - so the mod does not depend on any internal API of Create,
 * Create: Aeronautics / Simulated, Sable or Valkyrien Skies.</p>
 */
public class PhysicsSource {

    /** One recorded state of the object, used to replay sound with a real travel delay. */
    public record Shock(long tick, Vec3 pos, double speed, boolean supersonic) {
    }

    public final int entityId;
    public final Entity entity;

    /** Position at the start of the current tick. */
    public Vec3 pos;
    /** Velocity in blocks per tick, exponentially smoothed. */
    public Vec3 velocity = Vec3.ZERO;
    /** Raw velocity of the last tick (blocks per tick). */
    public Vec3 rawVelocity = Vec3.ZERO;
    /** Characteristic size (bounding box diagonal, blocks). */
    public double size = 1.0;
    /** Frontal area proxy, used for loudness scaling. */
    public double frontalArea = 1.0;

    public long lastSeenTick;
    public long lastBoomTick = Long.MIN_VALUE;
    public long lastWhooshTick = Long.MIN_VALUE;
    /** Closest approach bookkeeping for the pass-by whoosh. */
    public double previousDistanceToListener = Double.MAX_VALUE;

    public final Deque<Shock> trail = new ArrayDeque<>();

    /** True between crossing Mach 1 and the moment the cone has been heard once. */
    public boolean boomArmed;
    /** Whether the object was supersonic on the previous tick. */
    public boolean wasSupersonic;

    public PhysicsSource(Entity entity, long tick) {
        this.entity = entity;
        this.entityId = entity.getId();
        this.pos = entity.position();
        this.lastSeenTick = tick;
        updateSize(entity.getBoundingBox());
    }

    public void update(long tick) {
        Vec3 now = entity.position();
        Vec3 delta = now.subtract(pos);

        // Some contraption implementations move their entity by teleporting it every
        // tick, others keep a proper delta movement. Prefer the observed delta and
        // fall back to the reported one, then smooth to kill network jitter.
        if (delta.lengthSqr() < 1.0e-8) {
            Vec3 dm = entity.getDeltaMovement();
            if (dm.lengthSqr() > 1.0e-8) {
                delta = dm;
            }
        }
        // Ignore teleports / dimension jumps.
        if (delta.lengthSqr() > 64.0 * 64.0) {
            delta = Vec3.ZERO;
        }

        this.rawVelocity = delta;
        this.velocity = velocity.scale(0.65).add(delta.scale(0.35));
        this.pos = now;
        this.lastSeenTick = tick;
        updateSize(entity.getBoundingBox());
    }

    private void updateSize(AABB box) {
        double dx = Math.max(box.getXsize(), 0.2);
        double dy = Math.max(box.getYsize(), 0.2);
        double dz = Math.max(box.getZsize(), 0.2);
        this.size = Math.sqrt(dx * dx + dy * dy + dz * dz);
        this.frontalArea = Math.max(dx * dy, Math.max(dz * dy, dx * dz));
    }

    /** Speed in blocks per second. */
    public double speed() {
        return velocity.length() * 20.0;
    }

    public double rawSpeed() {
        return rawVelocity.length() * 20.0;
    }

    /** Centre of the object, used as the acoustic source point. */
    public Vec3 soundPos() {
        return entity.getBoundingBox().getCenter();
    }

    public void recordShock(long tick, double speedOfSoundPerSecond) {
        double s = speed();
        boolean supersonic = s >= speedOfSoundPerSecond;
        if (supersonic && !wasSupersonic) {
            boomArmed = true; // rising edge: a fresh Mach cone was born
        }
        wasSupersonic = supersonic;
        if (!supersonic && trail.isEmpty()) {
            return; // nothing to remember while the object is slow
        }
        trail.addLast(new Shock(tick, soundPos(), s, supersonic));
        while (trail.size() > 200) {
            trail.removeFirst();
        }
    }
}
