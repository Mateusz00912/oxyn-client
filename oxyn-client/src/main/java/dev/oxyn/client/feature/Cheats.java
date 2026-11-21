package dev.oxyn.client.feature;

import dev.oxyn.client.module.Module;
import dev.oxyn.client.module.ModuleManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

/**
 * Moduły "cheatowe" (Ruch, Walka, Świat). Używać wyłącznie na serwerach, które na to pozwalają
 * (np. anarchiczne, własny serwer, singleplayer).
 */
public final class Cheats {
    private static final Set<Block> XRAY = new HashSet<>();
    private static boolean xrayWas;
    private static long lastClick;
    private static int flyTicks;

    static {
        Block[] b = {
                Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
                Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
                Blocks.NETHER_GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
                Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.NETHER_QUARTZ_ORE,
                Blocks.ANCIENT_DEBRIS, Blocks.SPAWNER, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST,
                Blocks.BARREL, Blocks.SHULKER_BOX, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN,
                Blocks.WATER, Blocks.LAVA, Blocks.TNT, Blocks.END_PORTAL_FRAME, Blocks.END_PORTAL,
                Blocks.NETHER_PORTAL, Blocks.DIAMOND_BLOCK, Blocks.GOLD_BLOCK, Blocks.IRON_BLOCK,
                Blocks.EMERALD_BLOCK, Blocks.NETHERITE_BLOCK
        };
        for (Block x : b) XRAY.add(x);
    }

    private Cheats() {}

    public static boolean isXrayBlock(BlockState s) {
        return XRAY.contains(s.getBlock());
    }

    public static void tick(MinecraftClient mc, ClientPlayerEntity p) {
        xray(mc);
        if (mc.world == null) return;
        fly(mc, p);
        speed(mc, p);
        noFall(p);
        killAura(mc, p);
        autoClicker(mc, p);
    }

    // ------------------------------------------------------------ Xray
    private static void xray(MinecraftClient mc) {
        boolean on = ModuleManager.on("xray");
        if (on != xrayWas) {
            xrayWas = on;
            if (mc.worldRenderer != null) mc.worldRenderer.reload(); // przeładuj chunki
        }
    }

    // ------------------------------------------------------------ Fly
    private static void fly(MinecraftClient mc, ClientPlayerEntity p) {
        if (!ModuleManager.on("fly") || p.isSpectator()) {
            flyTicks = 0;
            return;
        }
        Module m = ModuleManager.get("fly");
        double sp = m.num("Prędkość") * 0.5;
        double vsp = m.num("Prędkość pionowa") * 0.4;

        double fwd = p.input.movementForward;
        double side = p.input.movementSideways;
        double yawRad = Math.toRadians(p.getYaw());
        double vx = 0, vz = 0;
        if (fwd != 0 || side != 0) {
            double len = Math.max(1.0, Math.hypot(fwd, side));
            fwd /= len;
            side /= len;
            vx = (-Math.sin(yawRad) * fwd + Math.cos(yawRad) * side) * sp;
            vz = (Math.cos(yawRad) * fwd + Math.sin(yawRad) * side) * sp;
        }
        double vy = 0;
        if (mc.options.jumpKey.isPressed()) vy += vsp;
        if (mc.options.sneakKey.isPressed()) vy -= vsp;

        // lekki dryf w dół co ~20 ticków, żeby serwer nie wyrzucał za "floating"
        flyTicks++;
        if (m.flag("Antykopnięcie (lekki dryf)") && vy == 0 && flyTicks % 20 == 0) vy = -0.04;

        p.setVelocity(vx, vy, vz);
        p.fallDistance = 0;
    }

    // ------------------------------------------------------------ Speed
    private static void speed(MinecraftClient mc, ClientPlayerEntity p) {
        if (!ModuleManager.on("speedhack") || ModuleManager.on("fly")) return;
        Module m = ModuleManager.get("speedhack");
        if (m.flag("Tylko na ziemi") && !p.isOnGround()) return;
        if (p.input.movementForward == 0 && p.input.movementSideways == 0) return;
        if (p.isSneaking() || p.isTouchingWater() || p.isClimbing()) return;

        double mult = m.num("Mnożnik");
        double fwd = p.input.movementForward, side = p.input.movementSideways;
        double len = Math.max(1.0, Math.hypot(fwd, side));
        fwd /= len;
        side /= len;
        double yawRad = Math.toRadians(p.getYaw());
        double base = 0.2873 * mult; // ~ prędkość sprintu
        double vx = (-Math.sin(yawRad) * fwd + Math.cos(yawRad) * side) * base;
        double vz = (Math.cos(yawRad) * fwd + Math.sin(yawRad) * side) * base;
        p.setVelocity(vx, p.getVelocity().y, vz);
    }

    // ------------------------------------------------------------ NoFall
    private static void noFall(ClientPlayerEntity p) {
        if (!ModuleManager.on("nofall") || p.isSpectator() || p.getAbilities().flying) return;
        if (p.fallDistance > 2.0f && !p.isOnGround()) {
            p.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }
    }

    // ------------------------------------------------------------ KillAura
    private static void killAura(MinecraftClient mc, ClientPlayerEntity p) {
        if (!ModuleManager.on("killaura") || mc.interactionManager == null || mc.currentScreen != null) return;
        if (p.isSpectator() || !p.isAlive()) return;
        Module m = ModuleManager.get("killaura");
        if (p.getAttackCooldownProgress(0f) < m.num("Min. ładowanie ataku")) return;

        double range = m.num("Zasięg");
        boolean players = m.flag("Gracze"), mobs = m.flag("Moby");
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || e == p || !le.isAlive() || le.isInvisible()) continue;
            boolean ok = (e instanceof PlayerEntity && players) || (e instanceof MobEntity && mobs);
            if (!ok) continue;
            double d = p.distanceTo(le);
            if (d <= range && d < bestD) {
                bestD = d;
                best = le;
            }
        }
        if (best == null) return;

        if (m.flag("Obrót w stronę celu")) {
            Vec3d eye = p.getEyePos();
            Vec3d t = best.getPos().add(0, best.getHeight() * 0.6, 0);
            double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
            double h = Math.hypot(dx, dz);
            p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
            p.setPitch(MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, h)), -90f, 90f));
        }
        mc.interactionManager.attackEntity(p, best);
        p.swingHand(Hand.MAIN_HAND);
    }

    // ------------------------------------------------------------ AutoClicker
    private static void autoClicker(MinecraftClient mc, ClientPlayerEntity p) {
        if (!ModuleManager.on("autoclicker") || mc.interactionManager == null || mc.currentScreen != null) return;
        if (ModuleManager.on("killaura")) return;
        if (!mc.options.attackKey.isPressed()) return;
        Module m = ModuleManager.get("autoclicker");
        long now = System.currentTimeMillis();
        long gap = (long) (1000.0 / m.num("CPS"));
        if (now - lastClick < gap) return;

        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            Entity target = ((net.minecraft.util.hit.EntityHitResult) mc.crosshairTarget).getEntity();
            mc.interactionManager.attackEntity(p, target);
            p.swingHand(Hand.MAIN_HAND);
            lastClick = now;
        } else if (!m.flag("Tylko na cel")) {
            p.swingHand(Hand.MAIN_HAND);
            lastClick = now;
        }
    }
}
