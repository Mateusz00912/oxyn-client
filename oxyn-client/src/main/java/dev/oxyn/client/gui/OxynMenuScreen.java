package dev.oxyn.client.gui;

import dev.oxyn.client.module.Category;
import dev.oxyn.client.module.Module;
import dev.oxyn.client.module.ModuleManager;
import dev.oxyn.client.module.Setting;
import dev.oxyn.client.render.Glass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Menu modułów otwierane Prawym Shiftem. */
public class OxynMenuScreen extends Screen {
    private static final int W = 460, H = 280, SIDE = 104;

    private record Hit(int x, int y, int w, int h, Runnable click, Setting slider) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final Screen parent;
    private int tab = 0; // 0..2 kategorie, 3 ogólne
    private double scroll;
    private double contentHeight;
    private Setting dragging;
    private int dragX, dragW;
    private final List<Hit> hits = new ArrayList<>();

    public OxynMenuScreen(Screen parent) {
        super(Text.literal("Oxyn"));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0x80060C1E, 0xC0030612);
    }

    @Override
    public void close() {
        ModuleManager.save();
        MinecraftClient.getInstance().setScreen(parent);
    }

    private static String[] tabNames() {
        Category[] c = Category.values();
        String[] n = new String[c.length + 2];
        for (int i = 0; i < c.length; i++) n[i] = c[i].label;
        n[c.length] = "Wygląd";
        n[c.length + 1] = "Ogólne";
        return n;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = textRenderer;
        hits.clear();

        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        Glass.panel(ctx, x0, y0, W, H, 12);

        // Pasek boczny
        ctx.drawText(tr, "OXYN", x0 + 14, y0 + 14, Glass.accentLight(), true);
        ctx.drawText(tr, " CLIENT", x0 + 14 + tr.getWidth("OXYN"), y0 + 14, 0xFFFFFFFF, true);
        ctx.drawText(tr, "Right Shift - zamknij", x0 + 14, y0 + H - 18, 0xFF7C93AB, false);
        String[] names = tabNames();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            int ty = y0 + 40 + i * 28;
            boolean sel = tab == i;
            if (sel) Glass.panelAccent(ctx, x0 + 8, ty, SIDE - 8, 22, 6, Glass.accent());
            else Glass.roundRect(ctx, x0 + 8, ty, SIDE - 8, 22, 6, 0x22FFFFFF);
            ctx.drawText(tr, names[i], x0 + 18, ty + 7, 0xFFFFFFFF, true);
            hits.add(new Hit(x0 + 8, ty, SIDE - 8, 22, () -> {
                tab = idx;
                scroll = 0;
            }, null));
        }
        ctx.fill(x0 + SIDE + 2, y0 + 12, x0 + SIDE + 3, y0 + H - 12, 0x33FFFFFF);

        // Zawartość
        int cx = x0 + SIDE + 12, cy = y0 + 12, cw = W - SIDE - 24, ch = H - 24;
        ctx.drawText(tr, names[tab], cx, cy + 2, 0xFFFFFFFF, true);
        int areaY = cy + 18, areaH = ch - 18;
        double maxScroll = Math.max(0, contentHeight - areaH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        ctx.enableScissor(cx - 2, areaY, cx + cw + 2, areaY + areaH);
        int y = (int) (areaY - scroll);
        int start = y;
        if (tab < Category.values().length) {
            for (Module m : ModuleManager.inCategory(Category.values()[tab])) {
                y = moduleRow(ctx, tr, m, cx, y, cw, areaY, areaH);
            }
        } else if (tab == Category.values().length) {
            y = appearanceRows(ctx, tr, cx, y, cw, areaY, areaH);
        } else {
            y = generalRows(ctx, tr, cx, y, cw, areaY, areaH);
        }
        contentHeight = y - start;
        ctx.disableScissor();

        if (maxScroll > 0) {
            int trackH = areaH;
            int barH = (int) Math.max(16, trackH * (areaH / contentHeight));
            int barY = areaY + (int) ((trackH - barH) * (scroll / maxScroll));
            ctx.fill(cx + cw + 3, barY, cx + cw + 5, barY + barH, 0x88FFFFFF);
        }
    }

    private boolean visible(int y, int h, int areaY, int areaH) {
        return y + h > areaY && y < areaY + areaH;
    }

    private void addHit(Hit h, int areaY, int areaH) {
        // klikalne tylko w widocznym obszarze
        if (h.y + h.h > areaY && h.y < areaY + areaH) hits.add(h);
    }

    private int moduleRow(DrawContext ctx, TextRenderer tr, Module m, int x, int y, int w, int areaY, int areaH) {
        int rh = 28;
        if (visible(y, rh, areaY, areaH)) {
            Glass.roundRect(ctx, x, y, w, rh - 2, 6, m.enabled ? 0x401E6FD0 : 0x24FFFFFF);
            ctx.drawText(tr, m.name, x + 8, y + 5, 0xFFFFFFFF, true);
            ctx.drawText(tr, tr.trimToWidth(m.desc, w - 80), x + 8, y + 16, 0xFF8FA3BA, false);
            if (!m.settings.isEmpty()) {
                ctx.drawText(tr, m.expanded ? "v" : ">", x + w - 52, y + 10, 0xFFB8D4F0, false);
            }
            toggleSwitch(ctx, x + w - 34, y + 7, m.enabled);
            final Module mod = m;
            addHit(new Hit(x + w - 38, y, 38, rh - 2, () -> mod.enabled = !mod.enabled, null), areaY, areaH);
            addHit(new Hit(x, y, w - 38, rh - 2, () -> {
                if (mod.settings.isEmpty()) mod.enabled = !mod.enabled;
                else mod.expanded = !mod.expanded;
            }, null), areaY, areaH);
        }
        y += rh;
        if (m.expanded) {
            for (Setting s : m.settings) {
                if (visible(y, 20, areaY, areaH)) settingRow(ctx, tr, s, x + 10, y, w - 10, areaY, areaH);
                y += 20;
            }
            y += 4;
        }
        return y;
    }

    private void settingRow(DrawContext ctx, TextRenderer tr, Setting s, int x, int y, int w, int areaY, int areaH) {
        ctx.drawText(tr, tr.trimToWidth(s.name, 130), x, y + 6, 0xFFD6E6F5, false);
        if (s.toggle) {
            toggleSwitch(ctx, x + w - 34, y + 3, s.on());
            addHit(new Hit(x + w - 38, y, 38, 20, () -> s.value = s.on() ? 0 : 1, null), areaY, areaH);
        } else {
            int tx = x + 140, tw = w - 140 - 44;
            ctx.fill(tx, y + 9, tx + tw, y + 11, 0x55FFFFFF);
            double f = (s.value - s.min) / (s.max - s.min);
            int kx = tx + (int) (tw * f);
            ctx.fill(tx, y + 9, kx, y + 11, Glass.accent());
            Glass.roundRect(ctx, kx - 3, y + 5, 7, 10, 3, 0xFFFFFFFF);
            String val = Math.abs(s.value) >= 10 ? String.valueOf(Math.round(s.value)) : String.format("%.2f", s.value);
            ctx.drawText(tr, val, tx + tw + 8, y + 6, 0xFFFFFFFF, false);
            addHit(new Hit(tx - 4, y, tw + 8, 20, null, s), areaY, areaH);
            sliderGeom.put(s, new int[]{tx, tw});
        }
    }

    private final java.util.Map<Setting, int[]> sliderGeom = new java.util.HashMap<>();

    private void button(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, Runnable click, int areaY, int areaH) {
        Glass.panel(ctx, x, y, w, h, 6);
        ctx.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2 + 1, 0xFFFFFFFF, true);
        addHit(new Hit(x, y, w, h, click, null), areaY, areaH);
    }

    private int appearanceRows(DrawContext ctx, TextRenderer tr, int x, int y, int w, int areaY, int areaH) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // gotowe style
        if (visible(y, 14, areaY, areaH)) ctx.drawText(tr, "Gotowe style szkła", x + 2, y + 2, 0xFFB8D4F0, false);
        y += 14;
        String[] presets = {"Mleczne", "Ciemne", "Neon", "Kryształ"};
        int bw = (w - 3 * 6) / presets.length;
        if (visible(y, 24, areaY, areaH)) {
            for (int i = 0; i < presets.length; i++) {
                final String pr = presets[i];
                button(ctx, tr, x + i * (bw + 6), y, bw, 22, pr, () -> ModuleManager.preset(pr), areaY, areaH);
            }
        }
        y += 28;

        // suwaki motywu
        if (visible(y, 14, areaY, areaH)) ctx.drawText(tr, "Kolory i szkło (zmiany widać od razu)", x + 2, y + 2, 0xFFB8D4F0, false);
        y += 14;
        for (Setting s : ModuleManager.get("theme").settings) {
            if (visible(y, 20, areaY, areaH)) settingRow(ctx, tr, s, x + 4, y, w - 4, areaY, areaH);
            y += 20;
        }
        y += 6;

        // rozmiar i pozycje
        if (visible(y, 14, areaY, areaH)) ctx.drawText(tr, "Rozmiar i miejsce na ekranie", x + 2, y + 2, 0xFFB8D4F0, false);
        y += 14;
        Setting scale = ModuleManager.get("hudlayout").setting("Skala");
        if (visible(y, 20, areaY, areaH)) settingRow(ctx, tr, scale, x + 4, y, w - 4, areaY, areaH);
        y += 24;
        if (visible(y, 24, areaY, areaH)) {
            if (mc.player != null) {
                button(ctx, tr, x, y, w, 22, "Edytor HUD - przeciągaj elementy, kółko = rozmiar",
                        () -> mc.setScreen(new HudEditorScreen(this)), areaY, areaH);
            } else {
                ctx.drawText(tr, "Edytor HUD: wejdź do świata (Prawy Shift w grze).", x + 4, y + 7, 0xFFFFC66B, false);
            }
        }
        y += 28;
        if (visible(y, 24, areaY, areaH)) {
            button(ctx, tr, x, y, w, 22, "Resetuj układ HUD", ModuleManager::resetHudAll, areaY, areaH);
        }
        y += 28;
        return y;
    }

    private int generalRows(DrawContext ctx, TextRenderer tr, int x, int y, int w, int areaY, int areaH) {
        if (visible(y, 28, areaY, areaH)) {
            Glass.roundRect(ctx, x, y, w, 26, 6, 0x24FFFFFF);
            ctx.drawText(tr, "Własne menu główne Oxyn", x + 8, y + 5, 0xFFFFFFFF, true);
            ctx.drawText(tr, "Zmiana działa po powrocie do menu", x + 8, y + 16, 0xFF8FA3BA, false);
            toggleSwitch(ctx, x + w - 34, y + 7, ModuleManager.customTitle);
            addHit(new Hit(x, y, w, 26, () -> ModuleManager.customTitle = !ModuleManager.customTitle, null), areaY, areaH);
        }
        y += 28;
        if (visible(y, 40, areaY, areaH)) {
            ctx.drawText(tr, "Linki (Discord / Sklep) edytujesz w pliku:", x + 4, y + 4, 0xFFB8D4F0, false);
            ctx.drawText(tr, "config/oxyn.json", x + 4, y + 16, Glass.accentLight(), false);
        }
        y += 40;
        return y;
    }

    private void toggleSwitch(DrawContext ctx, int x, int y, boolean on) {
        Glass.roundRect(ctx, x, y, 26, 12, 6, on ? Glass.accent() : 0xFF3A4660);
        Glass.roundRect(ctx, x + (on ? 15 : 1), y + 1, 10, 10, 5, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (!h.in(mx, my)) continue;
                if (h.slider != null) {
                    dragging = h.slider;
                    int[] g = sliderGeom.get(h.slider);
                    dragX = g[0];
                    dragW = g[1];
                    updateSlider(mx);
                } else if (h.click != null) {
                    h.click.run();
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void updateSlider(double mx) {
        if (dragging == null) return;
        double f = Math.max(0, Math.min(1, (mx - dragX) / dragW));
        double v = dragging.min + f * (dragging.max - dragging.min);
        dragging.set(Math.round(v * 100.0) / 100.0);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            updateSlider(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            ModuleManager.save();
            return true;
        }
        ModuleManager.save();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        scroll -= vAmount * 22;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
