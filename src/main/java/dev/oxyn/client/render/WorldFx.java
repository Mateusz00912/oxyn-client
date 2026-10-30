package dev.oxyn.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.oxyn.client.feature.Targeting;
import dev.oxyn.client.module.Module;
import dev.oxyn.client.module.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Efekty w świecie 3D: smuga, aura, okrąg na celu oraz napisy nad graczami (HP, OXYN nick). */
public final class WorldFx {
    private static final List<Vec3d> TRAIL = new ArrayList<>();
    private static int verts;

    private WorldFx() {}

    // ---------------------------------------------------------------- tick
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        Module m = ModuleManager.get("trail");
        if (p == null || !m.enabled) {
            TRAIL.clear();
            return;
        }
        Vec3d now = new Vec3d(p.getX(), p.getY(), p.getZ());
        if (!TRAIL.isEmpty() && TRAIL.get(TRAIL.size() - 1).squaredDistanceTo(now) > 64) TRAIL.clear();
        double speed = Math.hypot(p.getX() - p.prevX, p.getZ() - p.prevZ);
        boolean moving = speed > 0.04 || !p.isOnGround();
        if (!m.flag("Tylko w ruchu") || moving) {
            TRAIL.add(now);
        } else if (!TRAIL.isEmpty()) {
            TRAIL.remove(0);
            if (!TRAIL.isEmpty()) TRAIL.remove(0);
        }
        int max = (int) m.num("Długość");
        while (TRAIL.size() > max) TRAIL.remove(0);
    }

    // ---------------------------------------------------------------- kształty
    private static void v(BufferBuilder b, Matrix4f m, double x, double y, double z, int argb) {
        b.vertex(m, (float) x, (float) y, (float) z).color(argb);
        verts++;
    }

    private static void quad(BufferBuilder b, Matrix4f m, int argb,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4) {
        v(b, m, x1, y1, z1, argb);
        v(b, m, x2, y2, z2, argb);
        v(b, m, x3, y3, z3, argb);
        v(b, m, x4, y4, z4, argb);
    }

    /** Płaski pierścień (lub łuk) w płaszczyźnie XZ. c = środek względem kamery. */
    private static void ring(BufferBuilder b, Matrix4f m, Vec3d c, double r, double th, int argb, double a0, double arc) {
        int segs = Math.max(6, (int) (48 * Math.min(1.0, arc / (Math.PI * 2)) + 6));
        double ri = r - th / 2, ro = r + th / 2;
        for (int i = 0; i < segs; i++) {
            double a = a0 + arc * i / segs;
            double a2 = a0 + arc * (i + 1) / segs;
            quad(b, m, argb,
                    c.x + Math.cos(a) * ri, c.y, c.z + Math.sin(a) * ri,
                    c.x + Math.cos(a) * ro, c.y, c.z + Math.sin(a) * ro,
                    c.x + Math.cos(a2) * ro, c.y, c.z + Math.sin(a2) * ro,
                    c.x + Math.cos(a2) * ri, c.y, c.z + Math.sin(a2) * ri);
        }
    }

    private static int lerpRgb(int a, int b, double f) {
        int r = (int) MathHelper.lerp(f, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) MathHelper.lerp(f, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) MathHelper.lerp(f, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    // ---------------------------------------------------------------- render (LAST)
    public static void renderShapes(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || ctx.matrixStack() == null) return;

        boolean trail = ModuleManager.on("trail") && TRAIL.size() > 1;
        boolean aura = ModuleManager.on("aura");
        boolean tring = ModuleManager.on("targetring") && Targeting.target != null;
        if (!trail && !aura && !tring) return;

        Vec3d cam = ctx.camera().getPos();
        Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
        float td = mc.getRenderTickCounter().getTickDelta(false);
        double t = (mc.world.getTime() + td) / 20.0;

        verts = 0;
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        Vec3d self = new Vec3d(MathHelper.lerp(td, p.prevX, p.getX()), MathHelper.lerp(td, p.prevY, p.getY()),
                MathHelper.lerp(td, p.prevZ, p.getZ()));

        if (trail) drawTrail(b, mat, cam, self, t, p);
        if (aura) drawAura(b, mat, self.subtract(cam), t);
        if (tring) drawTargetRing(b, mat, cam, Targeting.target, t, td);

        if (verts == 0) return;
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawTrail(BufferBuilder b, Matrix4f m, Vec3d cam, Vec3d head, double t, ClientPlayerEntity p) {
        Module mod = ModuleManager.get("trail");
        double size = mod.num("Rozmiar");
        boolean rainbow = mod.flag("Tęcza");
        boolean firstPerson = MinecraftClient.getInstance().options.getPerspective().isFirstPerson();

        List<Vec3d> pts = new ArrayList<>(TRAIL);
        pts.add(head);
        int n = pts.size();
        for (int i = 0; i < n - 1; i++) {
            Vec3d a = pts.get(i), c = pts.get(i + 1);
            if (firstPerson && (a.distanceTo(cam) < 1.4 || c.distanceTo(cam) < 1.4)) continue;
            double f = (i + 1) / (double) (n - 1);
            int alpha = (int) (190 * f * f + 20 * f);
            int rgb = rainbow ? (java.awt.Color.HSBtoRGB((float) ((t * 0.25 + f * 0.6) % 1.0), 0.75f, 1f) & 0xFFFFFF)
                    : lerpRgb(0x2B5BFF, 0x8FEBFF, f);
            int col = Glass.argb(alpha, rgb);
            double hw = size * (0.35 + 0.65 * f);
            double ax = a.x - cam.x, ay = a.y - cam.y + 0.9, az = a.z - cam.z;
            double cx = c.x - cam.x, cy = c.y - cam.y + 0.9, cz = c.z - cam.z;
            // pionowa wstęga
            quad(b, m, col, ax, ay - hw, az, ax, ay + hw, az, cx, cy + hw, cz, cx, cy - hw, cz);
            // pozioma wstęga (widoczna też od tyłu)
            double dx = cx - ax, dz = cz - az;
            double len = Math.hypot(dx, dz);
            if (len < 1e-4) continue;
            double px = -dz / len * hw, pz = dx / len * hw;
            quad(b, m, col, ax - px, ay, az - pz, ax + px, ay, az + pz, cx + px, cy, cz + pz, cx - px, cy, cz - pz);
        }
    }

    private static void drawAura(BufferBuilder b, Matrix4f m, Vec3d c, double t) {
        Module mod = ModuleManager.get("aura");
        double r = mod.num("Promień");
        double sp = mod.num("Prędkość");
        Vec3d base = c.add(0, 0.03, 0);
        ring(b, m, base, r, 0.07, Glass.argb(150, 0x3DA5FF), 0, Math.PI * 2);
        ring(b, m, base.add(0, 0.01, 0), r + 0.12, 0.10, Glass.argb(230, 0x9FE3FF), t * sp * 3, 1.7);
        ring(b, m, base.add(0, 0.01, 0), r + 0.12, 0.10, Glass.argb(230, 0x9FE3FF), t * sp * 3 + Math.PI, 1.7);
        for (int k = 0; k < 3; k++) {
            double f = (t * sp * 0.45 + k / 3.0) % 1.0;
            double rr = r * (0.8 + 0.25 * Math.sin(f * Math.PI));
            ring(b, m, base.add(0, f * 1.9, 0), rr, 0.045, Glass.argb((int) (170 * (1 - f)), 0x6CC3FF), t * sp, Math.PI * 2);
        }
    }

    private static void drawTargetRing(BufferBuilder b, Matrix4f m, Vec3d cam, PlayerEntity target, double t, float td) {
        Vec3d pos = new Vec3d(MathHelper.lerp(td, target.prevX, target.getX()), MathHelper.lerp(td, target.prevY, target.getY()),
                MathHelper.lerp(td, target.prevZ, target.getZ())).subtract(cam);
        double r = target.getWidth() * 0.5 + 0.6 + Math.sin(t * 4) * 0.04;
        int red = 0xFF4D6D;
        Vec3d floor = pos.add(0, 0.04, 0);
        ring(b, m, floor, r, 0.08, Glass.argb(170, red), 0, Math.PI * 2);
        ring(b, m, floor.add(0, 0.01, 0), r + 0.1, 0.12, Glass.argb(240, 0xFFB3C1), t * 4, 1.4);
        ring(b, m, floor.add(0, 0.01, 0), r + 0.1, 0.12, Glass.argb(240, 0xFFB3C1), t * 4 + Math.PI, 1.4);
        double h = target.getHeight();
        ring(b, m, pos.add(0, h * 0.5, 0), r * 0.9, 0.04, Glass.argb(110, red), -t * 2, Math.PI * 2);
        ring(b, m, pos.add(0, h + 0.05, 0), r * 0.6, 0.04, Glass.argb(130, red), t * 2, Math.PI * 2);
    }

    // ---------------------------------------------------------------- napisy (AFTER_ENTITIES)
    public static void renderTags(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || ctx.matrixStack() == null || ctx.consumers() == null) return;
        boolean hp = ModuleManager.on("hptags");
        boolean own = ModuleManager.on("ownnametag");
        if (!hp && !own) return;

        float td = mc.getRenderTickCounter().getTickDelta(false);
        Vec3d cam = ctx.camera().getPos();
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();
        double range = hp ? ModuleManager.get("hptags").num("Zasięg (bloki)") : 0;

        for (PlayerEntity pl : mc.world.getPlayers()) {
            double x = MathHelper.lerp(td, pl.prevX, pl.getX());
            double y = MathHelper.lerp(td, pl.prevY, pl.getY());
            double z = MathHelper.lerp(td, pl.prevZ, pl.getZ());
            double dist = cam.distanceTo(new Vec3d(x, y, z));
            float scale = 0.025f * (float) Math.max(1.0, dist / 14.0);

            if (pl == mc.player) {
                if (own && !firstPerson) {
                    Text label = Text.literal("OXYN ").styled(s -> s.withColor(0x3DA5FF).withBold(true))
                            .append(Text.literal(pl.getName().getString()).styled(s -> s.withColor(0xFFFFFF).withBold(false)));
                    tag(mc, ctx, label, x - cam.x, y - cam.y + pl.getHeight() + 0.5, z - cam.z, scale);
                }
                continue;
            }
            if (!hp || pl.isInvisible() || pl.isSpectator() || dist > range) continue;
            float cur = pl.getHealth() + pl.getAbsorptionAmount();
            float max = Math.max(1f, pl.getMaxHealth());
            float f = Math.max(0f, Math.min(1f, pl.getHealth() / max));
            int rgb = java.awt.Color.HSBtoRGB(f * 0.33f, 0.85f, 1f) & 0xFFFFFF;
            Text label = Text.literal("HP " + String.format("%.1f", cur)).styled(s -> s.withColor(rgb))
                    .append(Text.literal(" / " + (int) max).styled(s -> s.withColor(0xAAAAAA)));
            tag(mc, ctx, label, x - cam.x, y - cam.y + pl.getHeight() + 0.85, z - cam.z, scale);
        }
    }

    private static void tag(MinecraftClient mc, WorldRenderContext ctx, Text text, double x, double y, double z, float scale) {
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        ms.push();
        ms.translate(x, y, z);
        ms.multiply(ctx.camera().getRotation());
        ms.scale(-scale, -scale, scale);
        Matrix4f m = ms.peek().getPositionMatrix();
        TextRenderer tr = mc.textRenderer;
        float w = tr.getWidth(text);
        tr.draw(text, -w / 2f, 0, 0xFFFFFFFF, false, m, vcp, TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000, 0xF000F0);
        ms.pop();
    }
}
