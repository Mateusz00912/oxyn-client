package dev.oxyn.client.gui;

import dev.oxyn.client.module.ModuleManager;
import dev.oxyn.client.render.Glass;
import dev.oxyn.client.render.OxynHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Edytor układu HUD: przeciągnij element myszką, kółkiem zmień rozmiar, R = reset elementu.
 * Elementy rysuje normalny HUD (z przykładowymi danymi), a tu tylko obrysowujemy je i obsługujemy mysz.
 */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private String dragging;
    private String selected;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("Edytor HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        OxynHud.editing = true;
    }

    @Override
    public void removed() {
        OxynHud.editing = false;
        ModuleManager.save();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x38000000);
    }

    private String hit(double mx, double my) {
        // od końca - wyżej leżące elementy mają pierwszeństwo
        List<Map.Entry<String, OxynHud.Rect>> list = new ArrayList<>(OxynHud.RECTS.entrySet());
        for (int i = list.size() - 1; i >= 0; i--) {
            OxynHud.Rect r = list.get(i).getValue();
            if (mx >= r.x && mx <= r.x + r.w && my >= r.y && my <= r.y + r.h) return list.get(i).getKey();
        }
        return null;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = textRenderer;
        String hover = dragging != null ? dragging : hit(mouseX, mouseY);

        for (Map.Entry<String, OxynHud.Rect> e : OxynHud.RECTS.entrySet()) {
            OxynHud.Rect r = e.getValue();
            int x1 = (int) r.x - 2, y1 = (int) r.y - 2, x2 = (int) (r.x + r.w) + 2, y2 = (int) (r.y + r.h) + 2;
            boolean on = e.getKey().equals(hover) || e.getKey().equals(selected);
            int col = on ? Glass.accent() : 0x99FFFFFF;
            ctx.fill(x1, y1, x2, y1 + 1, col);
            ctx.fill(x1, y2 - 1, x2, y2, col);
            ctx.fill(x1, y1, x1 + 1, y2, col);
            ctx.fill(x2 - 1, y1, x2, y2, col);
            if (on) {
                String label = OxynHud.NAMES.getOrDefault(e.getKey(), e.getKey())
                        + String.format("  (%.0f%%)", ModuleManager.hud(e.getKey())[2] * 100);
                int lw = tr.getWidth(label) + 8;
                int ly = y1 - 13 < 0 ? y2 + 2 : y1 - 13;
                Glass.panel(ctx, x1, ly, lw, 12, 4);
                ctx.drawText(tr, label, x1 + 4, ly + 2, 0xFFFFFFFF, false);
            }
        }

        String l1 = "Edytor HUD";
        String l2 = "Przeciągnij element myszką   |   Kółko myszy = rozmiar   |   R = reset elementu   |   ESC = zapisz i wróć";
        int w = tr.getWidth(l2) + 24;
        int x = (width - w) / 2;
        int y = height / 2 - 70;
        Glass.panel(ctx, x, y, w, 34, 9);
        ctx.drawText(tr, l1, (width - tr.getWidth(l1)) / 2, y + 6, Glass.accentLight(), true);
        ctx.drawText(tr, l2, x + 12, y + 20, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            String h = hit(mx, my);
            if (h != null) {
                dragging = h;
                selected = h;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            OxynHud.Rect r = OxynHud.RECTS.get(dragging);
            double space = r == null ? 1 : r.space;
            double[] st = ModuleManager.hud(dragging);
            st[0] += dx / space;
            st[1] += dy / space;
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
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        String h = dragging != null ? dragging : hit(mx, my);
        if (h != null) {
            selected = h;
            double[] st = ModuleManager.hud(h);
            st[2] = Math.max(0.4, Math.min(3.0, st[2] + vAmount * 0.05));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            String h = selected;
            if (h != null) {
                ModuleManager.resetHud(h);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
