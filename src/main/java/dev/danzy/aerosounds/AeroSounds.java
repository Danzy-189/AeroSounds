package dev.danzy.aerosounds;

import dev.danzy.aerosounds.config.AeroConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Aero Sounds - aerodynamic audio for physics contraptions.
 *
 * <p>The mod is completely client-side: it listens to what the client already
 * knows about moving objects (positions per tick) and synthesizes a realistic
 * acoustic image out of it - wind rush, pass-by whooshes, transonic rumble and
 * sonic booms, all with Doppler shift, a finite speed of sound, air absorption
 * and simple occlusion.</p>
 */
@Mod(AeroSounds.ID)
public class AeroSounds {

    public static final String ID = "aerosounds";

    public AeroSounds(IEventBus modBus, ModContainer container) {
        ModSounds.SOUNDS.register(modBus);
        container.registerConfig(ModConfig.Type.CLIENT, AeroConfig.SPEC);
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
