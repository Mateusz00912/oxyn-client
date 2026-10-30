package dev.oxyn.client.gui;

import dev.oxyn.client.module.ModuleManager;
import dev.oxyn.client.render.Glass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Menu główne Oxyn - neonowy układ z liquid glass. Rysowane w "wirtualnych" współrzędnych skalowanych do okna. */
public class OxynTitleScreen extends Screen {
    private record Btn(String label, String icon, String badge, int x, int y, int w, int h, Runnable action) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Btn> buttons = new ArrayList<>();
    private int vw, vh;
    private float scale = 1f;
    private int mid;

    public OxynTitleScreen() {
        super(Text.literal("Oxyn Client"));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        scale = Math.min(1f, height / 380f);
        vw = (int) (width / scale);
        vh = (int) (height / scale);
        mid = vh / 2 - 43; // cała zawartość mieści się w ~350 jednostkach i jest wyśrodkowana
        buttons.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        int cx = vw / 2;
        int bw = 250, bh = 30, gap = 6;
        int y = mid - 26;
        int x = cx - bw / 2;

        buttons.add(new Btn("Graj", "play", null, x, y, bw, bh, () -> mc.setScreen(new MultiplayerScreen(this))));
        y += bh + gap;
        buttons.add(new Btn("Ustawienia", "U", null, x, y, bw, bh, () -> mc.setScreen(new OptionsScreen(this, mc.options))));
        y += bh + gap;
        buttons.add(new Btn("Sklep Oxyn", "S", "WKRÓTCE", x, y, bw, bh, () -> open(ModuleManager.storeUrl)));
        y += bh + gap;
        buttons.add(new Btn("Moduły", "M", null, x, y, bw, bh, () -> mc.setScreen(new OxynMenuScreen(this))));
        y += bh + gap;
        buttons.add(new Btn("Discord", "D", ">>", x, y, bw, bh, () -> open(ModuleManager.discordUrl)));
        y += bh + 8;

        // rząd małych przycisków
        int sz = 28, sg = 8;
        int sx = cx - (4 * sz + 3 * sg) / 2;
        buttons.add(new Btn("", "K", null, sx, y, sz, sz, () -> {}));
        buttons.add(new Btn("", "J", null, sx + (sz + sg), y, sz, sz,
                () -> mc.setScreen(new LanguageOptionsScreen(this, mc.options, mc.getLanguageManager()))));
        buttons.add(new Btn("", "A", null, sx + 2 * (sz + sg), y, sz, sz,
                () -> mc.setScreen(new AccessibilityOptionsScreen(this, mc.options))));
        buttons.add(new Btn("", "X", null, sx + 3 * (sz + sg), y, sz, sz, mc::scheduleStop));
        y += sz + 8;

        buttons.add(new Btn("Wyjdź z gry", "<", null, x, y, 120, 26, mc::scheduleStop));
        buttons.add(new Btn("Tryb jednoosobowy", "1", null, x + 130, y, 120, 26, () -> mc.setScreen(new SelectWorldScreen(this))));
    }

    private static void open(String url) {
        try {
            Util.getOperatingSystem().open(URI.create(url));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta); // tło panoramy
        ctx.fillGradient(0, 0, width, height, 0x400A2A66, 0xA0050A20);

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        double mx = mouseX / scale, my = mouseY / scale;

        ctx.getMatrices().push();
        ctx.getMatrices().scale(scale, scale, 1f);

        int cx = vw / 2;

        // Logo
        logo(ctx, tr, cx, mid - 118);

        // Lewy górny badge
        Glass.panel(ctx, 8, 8, 150, 30, 8);
        ctx.drawText(tr, "Oxyn Client", 16, 14, 0xFFFFFFFF, true);
        ctx.drawText(tr, " v1.0.0", 16 + tr.getWidth("Oxyn Client"), 14, Glass.accent(), true);
        ctx.drawText(tr, "Wersja Minecraft: 1.21.1", 16, 25, 0xFFA8C4E0, false);

        // Prawy górny: lista funkcji
        String[] feats = {"Liquid Glass HUD", "Smuga i aura", "Okrąg na celu", "Spotify + tekst", "HP graczy", "i wiele więcej..."};
        for (int i = 0; i < feats.length; i++) {
            int w = tr.getWidth(feats[i]);
            int fy = 10 + i * 17;
            ctx.drawText(tr, feats[i], vw - w - 22, fy, 0xFFDDEBFA, true);
            ctx.fill(vw - 14, fy + 2, vw - 10, fy + 6, Glass.accent());
        }

        // Dolne narożniki
        ctx.drawText(tr, "Oxyn Client", 12, vh - 34, Glass.accent(), true);
        ctx.fill(12, vh - 22, 70, vh - 21, Glass.accent());
        ctx.drawText(tr, "Build 1.0.0", 12, vh - 16, 0xFF8FA3BA, false);
        String l1 = "Zaprojektowany dla graczy,", l2 = "przez graczy.";
        ctx.drawText(tr, l1, vw - tr.getWidth(l1) - 12, vh - 34, 0xFFDDEBFA, true);
        ctx.drawText(tr, l2, vw - tr.getWidth(l2) - 12, vh - 24, 0xFFDDEBFA, true);
        ctx.drawText(tr, "Oxyn.", vw - tr.getWidth("Oxyn.") - 12, vh - 13, Glass.accent(), true);

        // Przyciski
        String tooltip = null;
        for (Btn b : buttons) {
            boolean hov = b.hit(mx, my);
            boolean first = b == buttons.get(0);
            if (hov || first) Glass.panelAccent(ctx, b.x, b.y, b.w, b.h, 7, hov ? Glass.accentLight() : Glass.accent());
            else Glass.panel(ctx, b.x, b.y, b.w, b.h, 7);

            int iy = b.y + (b.h - 12) / 2;
            if (b.icon.equals("play")) {
                for (int c = 0; c < 10; c++) {
                    int hh = 14 - c * 14 / 10;
                    ctx.fill(b.x + 14 + c, b.y + b.h / 2 - hh / 2, b.x + 15 + c, b.y + b.h / 2 + hh / 2 + 1, Glass.accentLight());
                }
            } else if (!b.label.isEmpty()) {
                ctx.drawText(tr, b.icon, b.x + 15, iy + 2, Glass.accentLight(), true);
            } else {
                ctx.drawText(tr, b.icon, b.x + (b.w - tr.getWidth(b.icon)) / 2, b.y + (b.h - 8) / 2, 0xFFFFFFFF, true);
            }
            if (!b.label.isEmpty()) {
                boolean small = b.w < 200;
                int tx = small ? b.x + 14 : b.x + 46;
                if (small) ctx.drawText(tr, b.label, b.x + (b.w - tr.getWidth(b.label)) / 2, b.y + (b.h - 8) / 2, 0xFFFFFFFF, true);
                else ctx.drawText(tr, b.label, tx, b.y + (b.h - 8) / 2, 0xFFFFFFFF, true);
            }
            if (b.badge != null) {
                int bw = tr.getWidth(b.badge) + 12;
                int bx = b.x + b.w - bw - 8;
                if (b.badge.equals(">>")) {
                    ctx.drawText(tr, b.badge, b.x + b.w - 20, b.y + (b.h - 8) / 2, Glass.accentLight(), true);
                } else {
                    Glass.roundRect(ctx, bx, b.y + 7, bw, 16, 5, 0xAA1E5FB4);
                    ctx.drawText(tr, b.badge, bx + 6, b.y + 11, 0xFFFFFFFF, false);
                }
            }
            if (hov && b.icon.equals("K")) tooltip = mc.getSession().getUsername();
            else if (hov && b.icon.equals("J")) tooltip = "Język";
            else if (hov && b.icon.equals("A")) tooltip = "Dostępność";
            else if (hov && b.icon.equals("X")) tooltip = "Wyjdź";
        }
        if (tooltip != null) {
            int tw = tr.getWidth(tooltip) + 10;
            Glass.panel(ctx, (int) mx - tw / 2, (int) my - 22, tw, 16, 5);
            ctx.drawText(tr, tooltip, (int) mx - tw / 2 + 5, (int) my - 18, 0xFFFFFFFF, true);
        }

        ctx.getMatrices().pop();
    }

    private void logo(DrawContext ctx, TextRenderer tr, int cx, int y) {
        // korona
        int ky = y - 14;
        ctx.fill(cx - 12, ky + 8, cx + 12, ky + 12, 0xFF6CC3FF);
        for (int i = -1; i <= 1; i++) {
            ctx.fill(cx + i * 9 - 2, ky + (i == 0 ? 0 : 3), cx + i * 9 + 3, ky + 8, 0xFF9FE3FF);
        }
        // OXYN
        String word = "OXYN";
        float s = 6f;
        int[] cols = {0xFFFFFFFF, 0xFFD6EEFF, 0xFFA8DAFF, 0xFF74BFFF};
        int total = (int) (tr.getWidth(word) * s) + 3 * 4;
        float x = cx - total / 2f;
        for (int i = 0; i < word.length(); i++) {
            String ch = word.substring(i, i + 1);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + 3, y + 3, 0);
            ctx.getMatrices().scale(s, s, 1f);
            ctx.drawText(tr, ch, 0, 0, 0xFF1B4F9C, false);
            ctx.getMatrices().pop();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x, y, 0);
            ctx.getMatrices().scale(s, s, 1f);
            ctx.drawText(tr, ch, 0, 0, cols[i], false);
            ctx.getMatrices().pop();
            x += tr.getWidth(ch) * s + 4;
        }
        // CLIENT
        String c = "C L I E N T";
        float s2 = 1.8f;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx - tr.getWidth(c) * s2 / 2f, y + 56, 0);
        ctx.getMatrices().scale(s2, s2, 1f);
        ctx.drawText(tr, c, 0, 0, Glass.accent(), true);
        ctx.getMatrices().pop();
        // tagline
        Text tag = Text.literal("More than just a client.").styled(st -> st.withItalic(true));
        ctx.drawText(tr, tag, cx + 92, y + 12, 0xFFDDEBFA, true);
        ctx.drawText(tr, Text.literal("Oxyn").styled(st -> st.withItalic(true)), cx + 142, y + 24, Glass.accent(), true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double mx = mouseX / scale, my = mouseY / scale;
            for (Btn b : buttons) {
                if (b.hit(mx, my)) {
                    MinecraftClient.getInstance().getSoundManager()
                            .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    b.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
