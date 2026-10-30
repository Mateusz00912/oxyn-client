package dev.oxyn.client.render;

import dev.oxyn.client.feature.Spotify;
import dev.oxyn.client.feature.Targeting;
import dev.oxyn.client.module.Category;
import dev.oxyn.client.module.Module;
import dev.oxyn.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class OxynHud {
    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();
    private static boolean prevL, prevR;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private OxynHud() {}

    public static int cps(Deque<Long> q) {
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    private static void pollMouse(MinecraftClient mc) {
        long h = mc.getWindow().getHandle();
        boolean l = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        long now = System.currentTimeMillis();
        if (mc.currentScreen == null) {
            if (l && !prevL) LEFT.addLast(now);
            if (r && !prevR) RIGHT.addLast(now);
        }
        prevL = l;
        prevR = r;
    }

    public static void render(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;
        TextRenderer tr = mc.textRenderer;
        pollMouse(mc);

        // Automatyczna skala: HUD wygląda jak przy GUI scale 2, nawet gdy w grze jest 3 lub 4
        double auto = Math.min(1.0, 2.0 / mc.getWindow().getScaleFactor());
        float hs = (float) Math.max(0.3, auto * ModuleManager.get("hudlayout").num("Skala"));
        int realW = ctx.getScaledWindowWidth();
        int realH = ctx.getScaledWindowHeight();
        int sw = (int) (realW / hs);
        int sh = (int) (realH / hs);

        ctx.getMatrices().push();
        ctx.getMatrices().scale(hs, hs, 1f);

        // Góra pośrodku: nazwa / FPS / serwer + linijka tekstu piosenki
        if (ModuleManager.on("glasspanel")) {
            int bottom = topBar(ctx, mc, tr, sw);
            lyricPill(ctx, tr, sw, bottom + 4);
        } else {
            lyricPill(ctx, tr, sw, 6 + (int) ModuleManager.get("hudlayout").num("Panel górny Y"));
        }

        // Lewa kolumna: moduły, statystyki, klawisze
        int y = 6;
        if (ModuleManager.on("modlist")) y = modList(ctx, tr, 6, y);
        y = stats(ctx, mc, p, tr, 6, y);
        if (ModuleManager.on("keystrokes")) keystrokes(ctx, mc, tr, 6, y);

        // Prawa strona: efekty u góry, Spotify na dole
        if (ModuleManager.on("effects")) effects(ctx, p, tr, sw, 6);
        spotifyCard(ctx, tr, sw, sh);

        if (ModuleManager.on("targethud") && Targeting.target != null) targetHud(ctx, tr, sw, sh, Targeting.target, p);
        ctx.getMatrices().pop();

        // Pancerz obok hotbara - w prawdziwych współrzędnych GUI, żeby pasował do paska
        if (ModuleManager.on("armor")) armor(ctx, p, realW / 2 + 99, realH - 23);
    }

    private static int topBar(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int sw) {
        String server = mc.isInSingleplayer() ? "Singleplayer"
                : mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Offline";
        String fps = mc.getCurrentFps() + " FPS";
        String name = "OXYN CLIENT";
        int pad = 12, gap = 14;
        int w = pad + tr.getWidth(name) + gap + tr.getWidth(fps) + gap + tr.getWidth(server) + pad;
        int x = (sw - w) / 2;
        int y = 5 + (int) ModuleManager.get("hudlayout").num("Panel górny Y");
        Glass.panelAccent(ctx, x, y, w, 22, 9, 0x803DA5FF);
        int cx = x + pad;
        ctx.drawText(tr, "OXYN", cx, y + 7, Glass.ACCENT_LIGHT, true);
        ctx.drawText(tr, " CLIENT", cx + tr.getWidth("OXYN"), y + 7, 0xFFFFFFFF, true);
        cx += tr.getWidth(name) + gap / 2;
        ctx.fill(cx, y + 6, cx + 1, y + 16, 0x66FFFFFF);
        cx += gap / 2;
        ctx.drawText(tr, fps, cx, y + 7, 0xFFFFFFFF, true);
        cx += tr.getWidth(fps) + gap / 2;
        ctx.fill(cx, y + 6, cx + 1, y + 16, 0x66FFFFFF);
        cx += gap / 2;
        ctx.drawText(tr, server, cx, y + 7, 0xFFB8E0FF, true);
        return y + 22;
    }

    private static void lyricPill(DrawContext ctx, TextRenderer tr, int sw, int y) {
        if (!ModuleManager.on("spotify") || !Spotify.running || !Spotify.hasLyrics()) return;
        Module m = ModuleManager.get("spotify");
        if (!m.flag("Tekst piosenki")) return;
        long lp = Spotify.positionMs() + (long) (m.num("Przesunięcie tekstu (s)") * 1000);
        String cur = Spotify.lineAt(lp, 0);
        if (cur.isEmpty()) return;
        String t = tr.trimToWidth(cur, 320);
        int w = tr.getWidth(t) + 22;
        int x = (sw - w) / 2;
        Glass.panel(ctx, x, y, w, 18, 8);
        ctx.fill(x + 7, y + 6, x + 9, y + 12, 0xFF1ED760);
        ctx.drawText(tr, t, x + 13, y + 5, 0xFFFFFFFF, true);
    }

    private static int modList(DrawContext ctx, TextRenderer tr, int x, int y) {
        List<String> names = new ArrayList<>();
        for (Category c : new Category[]{Category.VISUAL, Category.UTILITY}) {
            for (Module m : ModuleManager.inCategory(c)) {
                if (m.enabled) names.add(m.name);
            }
        }
        if (ModuleManager.on("spotify")) names.add("Spotify");
        if (names.isEmpty()) return y;
        String title = "Moduły";
        int w = tr.getWidth(title) + 16;
        for (String n : names) w = Math.max(w, tr.getWidth(n) + 28);
        int h = 20 + names.size() * 11 + 3;
        Glass.panel(ctx, x, y, w, h, 8);
        ctx.drawText(tr, title, x + 8, y + 6, 0xFFFFFFFF, true);
        ctx.fill(x + 8, y + 16, x + 8 + tr.getWidth(title), y + 17, Glass.ACCENT);
        for (int i = 0; i < names.size(); i++) {
            int ly = y + 21 + i * 11;
            ctx.fill(x + 8, ly + 2, x + 11, ly + 5, Glass.ACCENT);
            ctx.drawText(tr, names.get(i), x + 16, ly, 0xFFDDEBFA, false);
        }
        return y + h + 6;
    }

    private static int stats(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p, TextRenderer tr, int x, int y) {
        List<String> l = new ArrayList<>();
        if (ModuleManager.on("cps")) l.add("CPS  " + cps(LEFT) + " | " + cps(RIGHT));
        if (ModuleManager.on("ping")) {
            PlayerListEntry e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            l.add("Ping  " + (e == null ? "-" : e.getLatency() + " ms"));
        }
        if (ModuleManager.on("speed")) {
            double v = Math.hypot(p.getX() - p.prevX, p.getZ() - p.prevZ) * 20.0;
            l.add(String.format("b/s  %.1f", v));
        }
        if (ModuleManager.on("coords")) l.add(String.format("xyz  %.0f, %.0f, %.0f", p.getX(), p.getY(), p.getZ()));
        if (ModuleManager.on("direction")) {
            String d = switch (p.getHorizontalFacing()) {
                case NORTH -> "N (-Z)";
                case SOUTH -> "S (+Z)";
                case WEST -> "W (-X)";
                default -> "E (+X)";
            };
            l.add("Kierunek  " + d);
        }
        if (ModuleManager.on("memory")) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
            l.add("RAM  " + used + " / " + (rt.maxMemory() / 1048576L) + " MB");
        }
        if (ModuleManager.on("clock")) l.add(LocalTime.now().format(CLOCK));
        if (l.isEmpty()) return y;
        int w = 0;
        for (String s : l) w = Math.max(w, tr.getWidth(s));
        w += 18;
        int h = l.size() * 11 + 7;
        Glass.panel(ctx, x, y, w, h, 8);
        ctx.fill(x + 5, y + 6, x + 7, y + h - 6, Glass.ACCENT);
        for (int i = 0; i < l.size(); i++) {
            ctx.drawText(tr, l.get(i), x + 11, y + 5 + i * 11, 0xFFFFFFFF, true);
        }
        return y + h + 6;
    }

    private static void keystrokes(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int x, int y) {
        key(ctx, tr, x + 26, y, 24, 24, "W", mc.options.forwardKey.isPressed());
        key(ctx, tr, x, y + 26, 24, 24, "A", mc.options.leftKey.isPressed());
        key(ctx, tr, x + 26, y + 26, 24, 24, "S", mc.options.backKey.isPressed());
        key(ctx, tr, x + 52, y + 26, 24, 24, "D", mc.options.rightKey.isPressed());
        key(ctx, tr, x, y + 52, 37, 22, "LPM " + cps(LEFT), prevL);
        key(ctx, tr, x + 39, y + 52, 37, 22, "PPM " + cps(RIGHT), prevR);
        key(ctx, tr, x, y + 76, 76, 14, "SPACJA", mc.options.jumpKey.isPressed());
    }

    private static void key(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, boolean down) {
        if (down) Glass.panelAccent(ctx, x, y, w, h, 5, Glass.ACCENT);
        else Glass.panel(ctx, x, y, w, h, 5);
        String t = tr.getWidth(label) > w - 2 ? label.substring(0, Math.min(3, label.length())) : label;
        ctx.drawText(tr, t, x + (w - tr.getWidth(t)) / 2, y + (h - 8) / 2 + 1, down ? 0xFFFFFFFF : 0xFFD6E6F5, true);
    }

    private static String mmss(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static void spotifyCard(DrawContext ctx, TextRenderer tr, int sw, int sh) {
        if (!ModuleManager.on("spotify") || !Spotify.running || Spotify.title.isEmpty()) return;
        int w = 176, h = 50;
        int x = sw - w - 6, y = sh - h - 6;
        Glass.panel(ctx, x, y, w, h, 9);
        Glass.roundRect(ctx, x + 6, y + 7, 34, 34, 7, 0xFF1ED760);
        Glass.roundRect(ctx, x + 8, y + 9, 30, 15, 6, 0x30FFFFFF);
        ctx.drawText(tr, Spotify.playing ? ">" : "||", x + 21 - (Spotify.playing ? 0 : 2), y + 20, 0xFF04120A, false);
        ctx.drawText(tr, tr.trimToWidth(Spotify.title, w - 54), x + 47, y + 7, 0xFFFFFFFF, true);
        ctx.drawText(tr, tr.trimToWidth(Spotify.artist, w - 54), x + 47, y + 18, 0xFFC4D6E8, false);
        long pos = Spotify.positionMs();
        int bx = x + 47, bw = w - 55;
        ctx.fill(bx, y + 31, bx + bw, y + 33, 0x55FFFFFF);
        if (Spotify.durationSec > 1) {
            double f = Math.min(1.0, pos / (Spotify.durationSec * 1000.0));
            ctx.fill(bx, y + 31, bx + (int) (bw * f), y + 33, 0xFF1ED760);
            String total = mmss((long) (Spotify.durationSec * 1000));
            ctx.drawText(tr, total, bx + bw - tr.getWidth(total), y + 37, 0xFFC4D6E8, false);
        }
        ctx.drawText(tr, mmss(pos), bx, y + 37, 0xFFC4D6E8, false);
    }

    private static void effects(DrawContext ctx, ClientPlayerEntity p, TextRenderer tr, int sw, int y) {
        List<String> lines = new ArrayList<>();
        List<Boolean> good = new ArrayList<>();
        for (StatusEffectInstance e : p.getStatusEffects()) {
            if (e.getDuration() > 100000) continue; // nasz efekt "jasność nocą"
            String name = e.getEffectType().value().getName().getString();
            if (e.getAmplifier() > 0) name += " " + (e.getAmplifier() + 1);
            int s = e.getDuration() / 20;
            lines.add(name + "  " + (s / 60) + ":" + String.format("%02d", s % 60));
            good.add(e.getEffectType().value().isBeneficial());
        }
        if (lines.isEmpty()) return;
        String title = "Efekty";
        int w = tr.getWidth(title) + 16;
        for (String l : lines) w = Math.max(w, tr.getWidth(l) + 28);
        int h = 20 + lines.size() * 11 + 3;
        int x = sw - w - 6;
        Glass.panel(ctx, x, y, w, h, 8);
        ctx.drawText(tr, title, x + 8, y + 6, 0xFFFFFFFF, true);
        ctx.fill(x + 8, y + 16, x + 8 + tr.getWidth(title), y + 17, Glass.ACCENT);
        for (int i = 0; i < lines.size(); i++) {
            int ly = y + 21 + i * 11;
            ctx.fill(x + 8, ly + 2, x + 11, ly + 5, good.get(i) ? 0xFF4CE08A : 0xFFFF5A5A);
            ctx.drawText(tr, lines.get(i), x + 16, ly, 0xFFDDEBFA, false);
        }
    }

    /** Pancerz: rząd 4 slotów w szklanym panelu obok hotbara (x,y w prawdziwych współrzędnych GUI). */
    private static void armor(DrawContext ctx, PlayerEntity p, int x, int y) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        MinecraftClient mc = MinecraftClient.getInstance();
        Glass.panel(ctx, x, y, 4 * 20 + 4, 22, 6);
        for (int i = 0; i < slots.length; i++) {
            ItemStack s = p.getEquippedStack(slots[i]);
            if (s.isEmpty()) continue;
            ctx.drawItem(s, x + 4 + i * 20, y + 3);
            ctx.drawItemInSlot(mc.textRenderer, s, x + 4 + i * 20, y + 3);
        }
    }

    private static void targetHud(DrawContext ctx, TextRenderer tr, int sw, int sh, PlayerEntity t, ClientPlayerEntity self) {
        int w = 150, h = 66;
        int x = sw / 2 - w / 2;
        int y = sh / 2 + 34;
        Glass.panelAccent(ctx, x, y, w, h, 9, 0xA0FF5A7A);
        ctx.drawText(tr, tr.trimToWidth(t.getName().getString(), w - 16), x + 8, y + 6, 0xFFFFFFFF, true);
        float hp = t.getHealth();
        float max = Math.max(1f, t.getMaxHealth());
        float f = Math.max(0f, Math.min(1f, hp / max));
        ctx.fill(x + 8, y + 19, x + w - 8, y + 25, 0x66000000);
        ctx.fill(x + 8, y + 19, x + 8 + (int) ((w - 16) * f), y + 25, Glass.hsb(f * 0.33f, 0.85f, 1f, 255));
        String info = String.format("%.1f / %.0f HP", hp + t.getAbsorptionAmount(), max)
                + String.format("   %.1fm", (double) self.distanceTo(t));
        ctx.drawText(tr, info, x + 8, y + 28, 0xFFD6E6F5, false);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        for (int i = 0; i < slots.length; i++) {
            ItemStack s = t.getEquippedStack(slots[i]);
            int ix = x + 8 + i * 22;
            Glass.roundRect(ctx, ix - 1, y + 42, 20, 20, 4, 0x30FFFFFF);
            if (!s.isEmpty()) ctx.drawItemInSlot(tr, s, ix + 1, y + 44);
        }
    }
}
