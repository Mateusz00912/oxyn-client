package dev.oxyn.client.feature;

import dev.oxyn.client.module.Module;
import dev.oxyn.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Animacja po zabiciu: fale w świecie, iskry, dźwięk i baner "ZABITO". */
public final class KillFx {
    public static final class Burst {
        public final Vec3d pos;
        public final long start;

        Burst(Vec3d pos, long start) {
            this.pos = pos;
            this.start = start;
        }
    }

    public static final List<Burst> BURSTS = new ArrayList<>();
    public static String bannerName = "";
    public static long bannerStart = 0;
    public static int streak = 0;

    private static LivingEntity attacked;
    private static long attackedAt;
    private static Vec3d lastPos = Vec3d.ZERO;
    private static long lastKill = 0;

    private KillFx() {}

    /** Wywoływane gdy gracz uderzy encję. */
    public static void onAttack(Entity e) {
        if (e instanceof LivingEntity le) {
            attacked = le;
            attackedAt = System.currentTimeMillis();
            lastPos = le.getPos();
        }
    }

    /** Czy baner jest teraz pokazywany. */
    public static boolean bannerActive() {
        Module m = ModuleManager.get("killfx");
        return m.enabled && m.flag("Baner") && bannerStart > 0
                && System.currentTimeMillis() - bannerStart < m.num("Czas banera (s)") * 1000;
    }

    public static void tick(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        BURSTS.removeIf(b -> now - b.start > 1700);
        if (attacked == null || mc.world == null) return;
        if (now - attackedAt > 8000) {
            attacked = null;
            return;
        }
        if (attacked.isAlive()) {
            lastPos = attacked.getPos();
            return;
        }
        trigger(mc, attacked.getName().getString(), lastPos, now);
        attacked = null;
    }

    private static void trigger(MinecraftClient mc, String name, Vec3d pos, long now) {
        Module m = ModuleManager.get("killfx");
        if (!m.enabled) return;
        streak = (now - lastKill < 8000) ? streak + 1 : 1;
        lastKill = now;
        bannerName = name;
        bannerStart = now;

        if (m.flag("Fale i iskry")) {
            BURSTS.add(new Burst(pos, now));
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 48; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double sp = 0.12 + r.nextDouble() * 0.25;
                mc.world.addParticle(ParticleTypes.END_ROD, pos.x, pos.y + 0.8, pos.z,
                        Math.cos(a) * sp, 0.05 + r.nextDouble() * 0.25, Math.sin(a) * sp);
            }
            for (int i = 0; i < 14; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                mc.world.addParticle(ParticleTypes.FIREWORK, pos.x, pos.y + 0.3, pos.z,
                        Math.cos(a) * 0.08, 0.15 + r.nextDouble() * 0.2, Math.sin(a) * 0.08);
            }
        }
        if (m.flag("Dźwięk")) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.3f));
        }
    }
}
