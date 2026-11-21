package dev.oxyn.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.oxyn.client.module.Module;
import dev.oxyn.client.module.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** ESP (ramki) i Tracers (linie) widoczne przez ściany. */
public final class EspFx {
    private EspFx() {}

    private static void line(BufferBuilder b, Matrix4f m, Vec3d a, Vec3d c, int argb) {
        b.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(argb);
        b.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(argb);
    }

    private static void box(BufferBuilder b, Matrix4f m, Box x, int col) {
        double x1 = x.minX, y1 = x.minY, z1 = x.minZ, x2 = x.maxX, y2 = x.maxY, z2 = x.maxZ;
        Vec3d[] v = {
                new Vec3d(x1, y1, z1), new Vec3d(x2, y1, z1), new Vec3d(x2, y1, z2), new Vec3d(x1, y1, z2),
                new Vec3d(x1, y2, z1), new Vec3d(x2, y2, z1), new Vec3d(x2, y2, z2), new Vec3d(x1, y2, z2)};
        int[][] e = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] i : e) line(b, m, v[i[0]], v[i[1]], col);
    }

    private static int colorFor(Entity e) {
        if (e instanceof PlayerEntity) return 0xFFFF4D6D;
        if (e instanceof MobEntity) return 0xFFFFC857;
        return 0xFF5CE1E6;
    }

    public static void render(WorldRenderContext ctx) {
        boolean esp = ModuleManager.on("esp");
        boolean tr = ModuleManager.on("tracers");
        if (!esp && !tr) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || ctx.matrixStack() == null) return;

        Vec3d cam = ctx.camera().getPos();
        Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
        float td = mc.getRenderTickCounter().getTickDelta(false);

        Module me = ModuleManager.get("esp"), mt = ModuleManager.get("tracers");
        double espR = me.num("Zasięg (bloki)"), trR = mt.num("Zasięg (bloki)");

        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        int count = 0;
        // początek linii: lekko przed kamerą, w kierunku patrzenia
        Vec3d start = p.getRotationVec(td).multiply(0.6);

        for (Entity e : mc.world.getEntities()) {
            if (e == p) continue;
            double d = e.distanceTo(p);
            double x = MathHelper.lerp(td, e.prevX, e.getX());
            double y = MathHelper.lerp(td, e.prevY, e.getY());
            double z = MathHelper.lerp(td, e.prevZ, e.getZ());
            Vec3d off = new Vec3d(x - e.getX(), y - e.getY(), z - e.getZ());
            Box bb = e.getBoundingBox().offset(off).offset(-cam.x, -cam.y, -cam.z);

            if (esp && d <= espR) {
                boolean ok = (e instanceof PlayerEntity && me.flag("Gracze"))
                        || (e instanceof MobEntity && me.flag("Moby"))
                        || (e instanceof ItemEntity && me.flag("Przedmioty"));
                if (ok) {
                    box(b, mat, bb, colorFor(e));
                    count++;
                }
            }
            if (tr && d <= trR && e instanceof LivingEntity) {
                boolean ok = e instanceof PlayerEntity || (e instanceof MobEntity && mt.flag("Moby"));
                if (ok) {
                    line(b, mat, start, bb.getCenter(), colorFor(e));
                    count++;
                }
            }
        }
        if (count == 0) return;

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0f);
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
