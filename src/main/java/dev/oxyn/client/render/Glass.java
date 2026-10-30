package dev.oxyn.client.render;

import net.minecraft.client.gui.DrawContext;

/** Pomocnicze rysowanie "liquid glass" w 2D (zaokrąglone, półprzezroczyste panele). */
public final class Glass {
    public static final int ACCENT = 0xFF3DA5FF;
    public static final int ACCENT_LIGHT = 0xFF9FD8FF;

    private Glass() {}

    public static void roundRect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
        if (h - 2 * r > 0) c.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Panel szkła: cień, mleczno-niebieska baza, jasna poświata od góry, cienka jasna obwódka. */
    public static void panel(DrawContext c, int x, int y, int w, int h, int r) {
        roundRect(c, x - 1, y + 1, w + 2, h + 2, r + 1, 0x28000000);
        roundRect(c, x - 1, y - 1, w + 2, h + 2, r + 1, 0x88DCEBFF);
        roundRect(c, x, y, w, h, r, 0x9A2F4573);
        roundRect(c, x + 1, y + 1, w - 2, Math.max(2, h / 2), Math.max(1, r - 1), 0x2EFFFFFF);
        c.fill(x + r, y + 1, x + w - r, y + 2, 0x66FFFFFF);
    }

    /** Wersja z akcentem (np. aktywny przycisk). */
    public static void panelAccent(DrawContext c, int x, int y, int w, int h, int r, int accent) {
        roundRect(c, x - 2, y - 2, w + 4, h + 4, r + 2, (accent & 0xFFFFFF) | 0x40000000);
        roundRect(c, x - 1, y - 1, w + 2, h + 2, r + 1, accent);
        roundRect(c, x, y, w, h, r, 0xD0102448);
        roundRect(c, x + 2, y + 1, w - 4, Math.max(2, h / 2 - 1), Math.max(1, r - 2), 0x2EFFFFFF);
    }

    public static int argb(int a, int rgb) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static int hsb(float hue, float sat, float bri, int alpha) {
        return (alpha << 24) | (java.awt.Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF);
    }
}
