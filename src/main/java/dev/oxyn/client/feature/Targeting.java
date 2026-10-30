package dev.oxyn.client.feature;

import dev.oxyn.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.world.RaycastContext;

/** Znajduje gracza na którego patrzymy (do okręgu i Target HUD). Zasięg większy niż w vanilli. */
public final class Targeting {
    public static PlayerEntity target;
    private static long lastSeen;

    private Targeting() {}

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity self = mc.player;
        if (self == null || mc.world == null) {
            target = null;
            return;
        }
        double range = 8;
        if (ModuleManager.on("targethud")) range = Math.max(range, ModuleManager.get("targethud").num("Zasięg (bloki)"));
        if (ModuleManager.on("targetring")) range = Math.max(range, ModuleManager.get("targetring").num("Zasięg (bloki)"));

        Vec3d start = self.getEyePos();
        Vec3d look = self.getRotationVec(1.0f);
        Vec3d end = start.add(look.multiply(range));
        Box box = self.getBoundingBox().stretch(look.multiply(range)).expand(1.0);

        EntityHitResult hit = ProjectileUtil.raycast(self, start, end, box,
                e -> e instanceof PlayerEntity && e != self && !e.isSpectator() && e.isAlive(),
                range * range);

        PlayerEntity found = null;
        if (hit != null) {
            Entity e = hit.getEntity();
            // Czy między nami a graczem nie ma ściany?
            HitResult block = mc.world.raycast(new RaycastContext(start, hit.getPos(),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, self));
            if (block.getType() == HitResult.Type.MISS && e instanceof PlayerEntity p) {
                found = p;
            }
        }
        if (found != null) {
            target = found;
            lastSeen = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - lastSeen > 600) {
            target = null;
        }
    }
}
