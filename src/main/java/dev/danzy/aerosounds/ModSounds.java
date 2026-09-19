package dev.danzy.aerosounds;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, AeroSounds.ID);

    /** Airy rush of a slow / medium speed object. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_LIGHT = register("wind_light");
    /** Deep, loaded roar of a big or very fast object. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_HEAVY = register("wind_heavy");
    /** Wind heard while riding a contraption (cabin / open cockpit noise). */
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_CABIN = register("wind_cabin");
    /** Single "vzhuh" of a close fast fly-by / drive-by. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WHOOSH_PASS = register("whoosh_pass");
    /** Trembling low rumble in the transonic region (Mach 0.85 - 1.0). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TRANSONIC_RUMBLE = register("transonic_rumble");
    /** Sharp double crack of an N-wave heard nearby. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SONIC_BOOM_NEAR = register("sonic_boom_near");
    /** Muffled, low-passed boom rolling in from far away. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SONIC_BOOM_FAR = register("sonic_boom_far");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(AeroSounds.rl(name)));
    }

    private ModSounds() {
    }
}
