package dev.oxyn.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.oxyn.client.module.Module;
import dev.oxyn.client.module.ModuleManager;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Nóż motylkowy (balisong) rysowany z prostopadłościanów. Dwie rączki obracają się wokół zawiasu,
 * a całość lekko się przechyla - cykliczny "flip". Ustawienia (pozycja, skala, obrót) są w menu modułu.
 */
public final class ButterflyKnife {
    private ButterflyKnife() {}

    private static float ease(double q) {
        q = Math.max(0, Math.min(1, q));
        return (float) (q * q * (3 - 2 * q));
    }

    public static void render(MatrixStack ms, boolean left) {
        Module m = ModuleManager.get("butterfly");
        float side = left ? -1f : 1f;
        double t = System.currentTimeMillis() / 1000.0 * m.num("Szybkość animacji");

        // faza flipa: 0..0.15 spokój, 0.15..0.85 obrót rączek, 0.85..1 spokój
        double phase = (t / 3.4) % 1.0;
        float q = ease((phase - 0.15) / 0.7);
        float handleA = 360f * q;
        float handleB = -360f * ease((phase - 0.2) / 0.7);
        float roll = 360f * ease((phase - 0.15) / 0.7) * 0.5f;
        float bob = (float) Math.sin(t * 2.0) * 3f;

        ms.push();
        // takie samo ułożenie jak dla zwykłego miecza w pierwszej osobie ("handheld")
        ms.translate(side * 1.13f / 16f, 3.2f / 16f, 1.13f / 16f);
        ms.multiply(new Quaternionf().rotationXYZ(0f, (float) Math.toRadians(-90 * side), (float) Math.toRadians(25 * side)));
        ms.scale(0.68f, 0.68f, 0.68f);
        // własne poprawki z menu
        ms.translate((float) m.num("Przesunięcie X"), (float) m.num("Przesunięcie Y"), (float) m.num("Przesunięcie Z"));
        float sc = (float) m.num("Skala");
        ms.scale(sc, sc, sc);
        ms.translate(-0.5f, -0.5f, -0.5f);
        // układ lokalny: oś X wzdłuż przekątnej (tak jak miecz), środek w środku modelu
        ms.translate(0.5f, 0.5f, 0.5f);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45f + (float) m.num("Obrót") + bob));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(roll));

        int accent = Glass.accent() & 0xFFFFFF;
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // ostrze (od zawiasu do czubka)
        box(b, ms, -0.02f, -0.040f, -0.012f, 0.50f, 0.040f, 0.012f, 0xD7E0EC);
        box(b, ms, 0.48f, -0.030f, -0.010f, 0.62f, 0.012f, 0.010f, 0xEEF4FB); // czubek
        box(b, ms, -0.02f, -0.040f, -0.004f, 0.50f, -0.030f, 0.004f, 0xFFFFFF); // błysk krawędzi

        // rączki obracają się wokół zawiasu (oś Z w punkcie x = -0.02)
        handle(b, ms, 0.034f, handleA, accent);
        handle(b, ms, -0.034f, handleB, accent);

        // zawias
        box(b, ms, -0.045f, -0.025f, -0.05f, 0.005f, 0.025f, 0.05f, accent);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableCull();
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.enableCull();
        ms.pop();
    }

    private static void handle(BufferBuilder b, MatrixStack ms, float z, float angle, int accent) {
        ms.push();
        ms.translate(-0.02f, 0f, 0f);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        box(b, ms, -0.52f, -0.036f, z - 0.014f, 0f, 0.036f, z + 0.014f, 0x2A2F3A);
        box(b, ms, -0.40f, -0.012f, z + (z > 0 ? 0.012f : -0.016f), -0.10f, 0.012f, z + (z > 0 ? 0.016f : -0.012f), accent);
        ms.pop();
    }

    private static void box(BufferBuilder b, MatrixStack ms, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        Matrix4f m = ms.peek().getPositionMatrix();
        face(b, m, rgb, 1.00f, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1); // +Z
        face(b, m, rgb, 0.62f, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0); // -Z
        face(b, m, rgb, 0.90f, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0); // +Y
        face(b, m, rgb, 0.55f, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1); // -Y
        face(b, m, rgb, 0.75f, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1); // +X
        face(b, m, rgb, 0.75f, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0); // -X
    }

    private static void face(BufferBuilder b, Matrix4f m, int rgb, float shade,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        int r = (int) (((rgb >> 16) & 255) * shade);
        int g = (int) (((rgb >> 8) & 255) * shade);
        int bl = (int) ((rgb & 255) * shade);
        int col = 0xFF000000 | (r << 16) | (g << 8) | bl;
        b.vertex(m, ax, ay, az).color(col);
        b.vertex(m, bx, by, bz).color(col);
        b.vertex(m, cx, cy, cz).color(col);
        b.vertex(m, dx, dy, dz).color(col);
    }
}
