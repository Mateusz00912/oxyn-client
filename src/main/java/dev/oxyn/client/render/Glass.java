package dev.oxyn.client.render;

import dev.oxyn.client.module.Module;
import dev.oxyn.client.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;

/** Rysowanie "liquid glass" w 2D. Wygląd zależy od motywu (zakładka Wygląd w menu pod Prawym Shiftem). */
public final class Glass {
    private Glass() {}

    private static Module th() {
        return ModuleManager.get("theme");
    }

    // ---------------------------------------------------------------- kolory
    public static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int hsb(float hue, float sat, float bri, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (java.awt.Color.HSBtoRGB(hue, Math.max(0f, Math.min(1f, sat)), Math.max(0f, Math.min(1f, bri))) & 0xFFFFFF);
    }

    public static int accent() {
        return hsb((float) (th().num("Akcent (barwa)") / 360.0), 0.76f, 1f, 255);
    }

    public static int accentLight() {
        return hsb((float) (th().num("Akcent (barwa)") / 360.0), 0.36f, 1f, 255);
    }

    public static int accentA(int alpha) {
        return argb(alpha, accent());
    }

    private static int lerp(int c1, int c2, float f) {
        int a = (int) (((c1 >>> 24) & 255) * (1 - f) + ((c2 >>> 24) & 255) * f);
        int r = (int) (((c1 >> 16) & 255) * (1 - f) + ((c2 >> 16) & 255) * f);
        int g = (int) (((c1 >> 8) & 255) * (1 - f) + ((c2 >> 8) & 255) * f);
        int b = (int) ((c1 & 255) * (1 - f) + (c2 & 255) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ---------------------------------------------------------------- kształty
    private static int inset(int r, int d) {
        if (d >= r) return 0;
        double dy = r - d - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    public static void roundRect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        roundRectV(c, x, y, w, h, r, color, color);
    }

    /** Zaokrąglony prostokąt z pionowym gradientem. */
    public static void roundRectV(DrawContext c, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < h; i++) {
            int in = inset(r, Math.min(i, h - 1 - i));
            float f = h <= 1 ? 0 : i / (float) (h - 1);
            int col = lerp(top, bottom, f);
            if ((col >>> 24) == 0) continue;
            c.fill(x + in, y + i, x + w - in, y + i + 1, col);
        }
    }

    /** Cienka obwódka (1 px) zgodna z zaokrągleniem, z gradientem od góry do dołu. */
    public static void outline(DrawContext c, int x, int y, int w, int h, int r, int top, int bottom) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < h; i++) {
            int d = Math.min(i, h - 1 - i);
            int in = inset(r, d);
            float f = h <= 1 ? 0 : i / (float) (h - 1);
            int col = lerp(top, bottom, f);
            if ((col >>> 24) == 0) continue;
            if (d == 0) {
                c.fill(x + in, y + i, x + w - in, y + i + 1, col);
            } else {
                int wid = Math.max(1, inset(r, d - 1) - in);
                c.fill(x + in, y + i, x + in + wid, y + i + 1, col);
                c.fill(x + w - in - wid, y + i, x + w - in, y + i + 1, col);
            }
        }
    }

    // ---------------------------------------------------------------- panele
    private static int rad(int r) {
        return Math.max(2, (int) Math.round(r * th().num("Zaokrąglenie")));
    }

    private static void body(DrawContext c, int x, int y, int w, int h, int rr) {
        Module t = th();
        int a = (int) (t.num("Krycie szkła") * 255);
        float hue = (float) (t.num("Barwa szkła") / 360.0);
        float sat = (float) t.num("Nasycenie");
        float bri = (float) t.num("Jasność");
        int top = hsb(hue, sat * 0.75f, Math.min(1f, bri * 1.3f), a);
        int bot = hsb(hue, sat, bri * 0.65f, Math.min(255, a + 35));
        roundRect(c, x - 1, y + 2, w + 2, h + 1, rr + 1, 0x26000000); // cień
        roundRectV(c, x, y, w, h, rr, top, bot);
        int sheen = (int) (t.num("Połysk") * 255);
        roundRectV(c, x + 1, y + 1, w - 2, Math.max(3, h * 55 / 100), Math.max(1, rr - 1), argb(sheen, 0xFFFFFF), argb(0, 0xFFFFFF));
    }

    public static void panel(DrawContext c, int x, int y, int w, int h, int r) {
        Module t = th();
        int rr = rad(r);
        body(c, x, y, w, h, rr);
        int ba = (int) (t.num("Obwódka") * 255);
        int sheen = (int) (t.num("Połysk") * 255);
        outline(c, x, y, w, h, rr, argb(ba, 0xFFFFFF), argb(ba / 3, 0xB8D8FF));
        // odbicie światła przy górnej krawędzi i delikatny blask u dołu
        c.fill(x + rr, y + 1, x + w - rr, y + 2, argb(Math.min(255, sheen * 2), 0xFFFFFF));
        c.fill(x + rr, y + h - 2, x + w - rr, y + h - 1, argb(sheen / 2, accent()));
    }

    /** Panel z kolorowym akcentem (aktywny przycisk, Target HUD). */
    public static void panelAccent(DrawContext c, int x, int y, int w, int h, int r, int accent) {
        int rr = rad(r);
        roundRect(c, x - 2, y - 2, w + 4, h + 4, rr + 2, (accent & 0xFFFFFF) | 0x30000000);
        body(c, x, y, w, h, rr);
        roundRectV(c, x, y, w, h, rr, (accent & 0xFFFFFF) | 0x50000000, (accent & 0xFFFFFF) | 0x20000000);
        outline(c, x, y, w, h, rr, accent | 0xFF000000, (accent & 0xFFFFFF) | 0x90000000);
    }
}
