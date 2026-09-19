package dev.danzy.aerosounds.client;

import dev.danzy.aerosounds.AeroSounds;
import dev.danzy.aerosounds.config.AeroConfig;
import dev.danzy.aerosounds.physics.PhysicsTracker;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.world.phys.Vec3;

/** Drives the whole simulation once per client tick. */
@EventBusSubscriber(modid = AeroSounds.ID, value = Dist.CLIENT)
public final class ClientEvents {

    private static Vec3 listenerPos = Vec3.ZERO;
    private static Vec3 listenerVel = Vec3.ZERO;

    public static Vec3 listenerPos() {
        return listenerPos;
    }

    /** Listener velocity in blocks per tick (used for the Doppler shift). */
    public static Vec3 listenerVelocity() {
        return listenerVel;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        if (!AeroConfig.C.enabled.get()) {
            return;
        }

        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 delta = camera.subtract(listenerPos);
        if (delta.lengthSqr() > 64.0 * 64.0) {
            delta = Vec3.ZERO; // teleport / respawn
        }
        listenerVel = listenerVel.scale(0.6).add(delta.scale(0.4));
        listenerPos = camera;

        PhysicsTracker.tick(mc.level, listenerPos);
        WindManager.tick(listenerPos);
        PassByManager.tick(listenerPos);
        SonicBoomManager.tick(listenerPos);
        CabinWindManager.tick();
        SoundScheduler.tick();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PhysicsTracker.clear();
            WindManager.clear();
            CabinWindManager.clear();
            SoundScheduler.clear();
            listenerVel = Vec3.ZERO;
        }
    }

    private ClientEvents() {
    }
}
