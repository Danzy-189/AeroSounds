package dev.danzy.aerosounds.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/** All tuning knobs of the mod. Client-side config: aerosounds-client.toml */
public final class AeroConfig {

    public static final ModConfigSpec SPEC;
    public static final AeroConfig C;

    static {
        Pair<AeroConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(AeroConfig::new);
        C = pair.getLeft();
        SPEC = pair.getRight();
    }

    // general
    public final ModConfigSpec.BooleanValue enabled;
    public final ModConfigSpec.DoubleValue masterVolume;
    public final ModConfigSpec.IntValue maxTrackedSources;

    // detection
    public final ModConfigSpec.ConfigValue<String> namespaces;
    public final ModConfigSpec.ConfigValue<String> classNameHints;
    public final ModConfigSpec.DoubleValue minSize;
    public final ModConfigSpec.BooleanValue includeVanillaVehicles;

    // physics of sound
    public final ModConfigSpec.DoubleValue speedOfSound;
    public final ModConfigSpec.BooleanValue propagationDelay;
    public final ModConfigSpec.BooleanValue doppler;
    public final ModConfigSpec.DoubleValue dopplerStrength;
    public final ModConfigSpec.BooleanValue occlusion;
    public final ModConfigSpec.DoubleValue occlusionStrength;
    public final ModConfigSpec.BooleanValue airAbsorption;
    public final ModConfigSpec.BooleanValue altitudeDensity;

    // wind
    public final ModConfigSpec.DoubleValue windVolume;
    public final ModConfigSpec.DoubleValue windMinSpeed;
    public final ModConfigSpec.DoubleValue windFullSpeed;
    public final ModConfigSpec.DoubleValue windMaxDistance;
    public final ModConfigSpec.DoubleValue cabinWindVolume;

    // pass-by
    public final ModConfigSpec.DoubleValue whooshVolume;
    public final ModConfigSpec.DoubleValue whooshMaxMissDistance;
    public final ModConfigSpec.DoubleValue whooshMinSpeed;

    // supersonic
    public final ModConfigSpec.DoubleValue boomVolume;
    public final ModConfigSpec.DoubleValue boomMaxDistance;
    public final ModConfigSpec.IntValue boomCooldownTicks;
    public final ModConfigSpec.BooleanValue transonicRumble;

    /** Splits a comma separated config value into a lower-cased set. */
    public static Set<String> split(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null) {
            return out;
        }
        Arrays.stream(raw.split(","))
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .filter(v -> !v.isEmpty())
                .forEach(out::add);
        return out;
    }

    private AeroConfig(ModConfigSpec.Builder b) {
        b.comment("Aero Sounds - aerodynamic audio for moving physics contraptions").push("general");
        enabled = b.comment("Master switch").define("enabled", true);
        masterVolume = b.comment("Global multiplier for every sound of this mod")
                .defineInRange("masterVolume", 1.0, 0.0, 4.0);
        maxTrackedSources = b.comment("Maximum number of simultaneously tracked moving objects")
                .defineInRange("maxTrackedSources", 32, 1, 256);
        b.pop();

        b.comment("Which entities are treated as physics contraptions").push("detection");
        namespaces = b.comment(
                        "Comma separated mod ids whose entities are considered physics objects.",
                        "Covers Create: Aeronautics (simulated/aeronautics/offroad/sable),",
                        "Create contraptions and Valkyrien Skies ships.")
                .define("namespaces", "simulated,aeronautics,offroad,sable,create,valkyrienskies,vs_eureka");
        classNameHints = b.comment(
                        "Comma separated fallback: entity classes or ids containing any of these are also tracked")
                .define("classNameHints", "physics,contraption,ship,vehicle,aircraft");
        minSize = b.comment("Minimum bounding box diagonal (blocks) for an object to make wind noise")
                .defineInRange("minSize", 1.4, 0.0, 64.0);
        includeVanillaVehicles = b.comment("Also give minecarts / boats aerodynamic noise")
                .define("includeVanillaVehicles", false);
        b.pop();

        b.comment("Acoustic model").push("acoustics");
        speedOfSound = b.comment(
                        "Speed of sound in blocks per second. 343 = real life (nothing in Minecraft",
                        "ever reaches it), 100 = arcade-realistic, booms happen at ~5 blocks/tick.")
                .defineInRange("speedOfSound", 100.0, 20.0, 343.0);
        propagationDelay = b.comment("Sound needs time to travel: distant events are heard later")
                .define("propagationDelay", true);
        doppler = b.comment("Pitch shift from relative radial velocity").define("doppler", true);
        dopplerStrength = b.comment("0 = off, 1 = physically correct, >1 = exaggerated")
                .defineInRange("dopplerStrength", 1.0, 0.0, 3.0);
        occlusion = b.comment("Muffle sounds behind walls / terrain").define("occlusion", true);
        occlusionStrength = b.defineInRange("occlusionStrength", 0.7, 0.0, 1.0);
        airAbsorption = b.comment("High frequencies fade with distance (far sounds become dull rumbles)")
                .define("airAbsorption", true);
        altitudeDensity = b.comment("Thin air high above the world makes aerodynamic noise quieter")
                .define("altitudeDensity", true);
        b.pop();

        b.comment("Wind rush of moving objects").push("wind");
        windVolume = b.defineInRange("windVolume", 1.0, 0.0, 4.0);
        windMinSpeed = b.comment("Speed (blocks/s) where the wind loop starts to be audible")
                .defineInRange("windMinSpeed", 6.0, 0.5, 100.0);
        windFullSpeed = b.comment("Speed (blocks/s) of maximum wind loudness")
                .defineInRange("windFullSpeed", 70.0, 5.0, 400.0);
        windMaxDistance = b.comment("Audible radius of the wind loop, blocks")
                .defineInRange("windMaxDistance", 160.0, 8.0, 512.0);
        cabinWindVolume = b.comment("Wind noise while you ride a contraption yourself (0 disables)")
                .defineInRange("cabinWindVolume", 0.9, 0.0, 4.0);
        b.pop();

        b.comment("Close fly-by / drive-by whoosh").push("passby");
        whooshVolume = b.defineInRange("whooshVolume", 1.0, 0.0, 4.0);
        whooshMaxMissDistance = b.comment("How close (blocks) the object must pass to trigger a whoosh")
                .defineInRange("whooshMaxMissDistance", 26.0, 1.0, 128.0);
        whooshMinSpeed = b.comment("Minimum speed (blocks/s) for a pass-by whoosh")
                .defineInRange("whooshMinSpeed", 18.0, 1.0, 300.0);
        b.pop();

        b.comment("Supersonic effects").push("supersonic");
        boomVolume = b.defineInRange("boomVolume", 1.0, 0.0, 4.0);
        boomMaxDistance = b.comment("Maximum distance (blocks) at which a sonic boom is still heard")
                .defineInRange("boomMaxDistance", 400.0, 16.0, 2048.0);
        boomCooldownTicks = b.comment("Minimum ticks between two booms from the same object")
                .defineInRange("boomCooldownTicks", 30, 1, 400);
        transonicRumble = b.comment("Rumbling buffet between Mach 0.85 and Mach 1.0")
                .define("transonicRumble", true);
        b.pop();
    }
}
