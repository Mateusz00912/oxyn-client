package dev.oxyn.client.render;

import dev.oxyn.client.feature.KillFx;
import dev.oxyn.client.feature.Spotify;
import dev.oxyn.client.feature.Targeting;
import dev.oxyn.client.module.Category;
import dev.oxyn.client.module.Module;
import dev.oxyn.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OxynHud {
    /** Prostokąt elementu w prawdziwych współrzędnych GUI (do edytora). */
    public static final class Rect {
        public double x, y, w, h;
        public double space = 1; // skala przestrzeni rysowania (do przeliczania przeciągania)
    }

    /** id -> nazwa w edytorze. */
    public static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("topbar", "Panel górny");
        NAMES.put("lyric", "Tekst piosenki");
        NAMES.put("modlist", "Lista modułów");
        NAMES.put("stats", "Statystyki");
        NAMES.put("keys", "Keystrokes");
        NAMES.put("effects", "Efekty");
        NAMES.put("spotify", "Spotify");
        NAMES.put("target", "Target HUD");
        NAMES.put("armor", "Pancerz");
        NAMES.put("kill", "Baner zabicia");
    }

    public static final Map<String, Rect> RECTS = new LinkedHashMap<>();
    /** true gdy otwarty jest edytor pozycji - wtedy pokazujemy przykładowe dane. */
    public static boolean editing = false;

    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();
    private static boolean prevL, prevR;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private interface Draw {
        int[] run(int ax, int ay);
    }

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

    /**
     * Rysuje element z zapisanym przesunięciem i skalą. (ax, ay) to punkt zaczepienia;
     * align: 0 = lewa krawędź, 0.5 = środek, 1 = prawa krawędź. Skalowanie odbywa się względem tego punktu.
     */
    private static int[] el(DrawContext ctx, String id, int ax, int ay, float align, double space, Draw d) {
        double[] st = ModuleManager.hud(id);
        ctx.getMatrices().push();
        ctx.getMatrices().translate((float) (ax + st[0]), (float) (ay + st[1]), 0f);
        ctx.getMatrices().scale((float) st[2], (float) st[2], 1f);
        ctx.getMatrices().translate((float) -ax, (float) -ay, 0f);
        int[] size = d.run(ax, ay);
        ctx.getMatrices().pop();
        if (size != null) {
            Rect r = RECTS.computeIfAbsent(id, k -> new Rect());
            double w = size[0] * st[2], h = size[1] * st[2];
            r.x = (ax + st[0] - align * w) * space;
            r.y = (ay + st[1]) * space;
            r.w = w * space;
            r.h = h * space;
            r.space = space;
        }
        return size;
    }

    public static void render(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        RECTS.clear();
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

        if (ModuleManager.on("glasspanel")) el(ctx, "topbar", sw / 2, 5, 0.5f, hs, (ax, ay) -> topBar(ctx, mc, tr, ax, ay));
        el(ctx, "lyric", sw / 2, 31, 0.5f, hs, (ax, ay) -> lyricPill(ctx, tr, ax, ay));

        // Lewa kolumna: moduły, statystyki, klawisze
        int y = 6;
        if (ModuleManager.on("modlist")) {
            int[] s = el(ctx, "modlist", 6, y, 0f, hs, (ax, ay) -> modList(ctx, tr, ax, ay));
            if (s != null) y += s[1] + 6;
        }
        {
            int yy = y;
            int[] s = el(ctx, "stats", 6, yy, 0f, hs, (ax, ay) -> stats(ctx, mc, p, tr, ax, ay));
            if (s != null) y += s[1] + 6;
        }
        if (ModuleManager.on("keystrokes")) {
            int yy = y;
            el(ctx, "keys", 6, yy, 0f, hs, (ax, ay) -> keystrokes(ctx, mc, tr, ax, ay));
        }

        // Prawa strona
        if (ModuleManager.on("effects")) el(ctx, "effects", sw - 6, 6, 1f, hs, (ax, ay) -> effects(ctx, p, tr, ax, ay));
        el(ctx, "spotify", sw - 6, sh - 72, 1f, hs, (ax, ay) -> spotifyCard(ctx, tr, ax, ay));

        if (ModuleManager.on("targethud")) {
            PlayerEntity t = Targeting.target != null ? Targeting.target : (editing ? p : null);
            if (t != null) el(ctx, "target", sw / 2, sh / 2 + 34, 0.5f, hs, (ax, ay) -> targetHud(ctx, tr, ax, ay, t, p));
        }
        if (KillFx.bannerActive() || editing) el(ctx, "kill", sw / 2, sh / 4, 0.5f, hs, (ax, ay) -> killBanner(ctx, tr, ax, ay));

        ctx.getMatrices().pop();

        // Pancerz obok hotbara - w prawdziwych współrzędnych GUI, żeby pasował do paska
        if (ModuleManager.on("armor")) el(ctx, "armor", realW / 2 + 99, realH - 23, 0f, 1.0, (ax, ay) -> armor(ctx, mc, p, ax, ay));
    }

    // ---------------------------------------------------------------- elementy
    private static int[] topBar(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int ax, int y) {
        String server = mc.isInSingleplayer() ? "Singleplayer"
                : mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Offline";
        String fps = mc.getCurrentFps() + " FPS";
        String name = "OXYN CLIENT";
        int pad = 12, gap = 14;
        int w = pad + tr.getWidth(name) + gap + tr.getWidth(fps) + gap + tr.getWidth(server) + pad;
        int x = ax - w / 2;
        Glass.panelAccent(ctx, x, y, w, 22, 9, Glass.accentA(0x90));
        int cx = x + pad;
        ctx.drawText(tr, "OXYN", cx, y + 7, Glass.accentLight(), true);
        ctx.drawText(tr, " CLIENT", cx + tr.getWidth("OXYN"), y + 7, 0xFFFFFFFF, true);
        cx += tr.getWidth(name) + gap / 2;
        ctx.fill(cx, y + 6, cx + 1, y + 16, 0x66FFFFFF);
        cx += gap / 2;
        ctx.drawText(tr, fps, cx, y + 7, 0xFFFFFFFF, true);
        cx += tr.getWidth(fps) + gap / 2;
        ctx.fill(cx, y + 6, cx + 1, y + 16, 0x66FFFFFF);
        cx += gap / 2;
        ctx.drawText(tr, server, cx, y + 7, 0xFFD6EBFF, true);
        return new int[]{w, 22};
    }

    private static int[] lyricPill(DrawContext ctx, TextRenderer tr, int ax, int y) {
        String cur = null;
        Module m = ModuleManager.get("spotify");
        if (m.enabled && m.flag("Tekst na HUD") && Spotify.running && Spotify.hasLyrics()) {
            long lp = Spotify.positionMs() + (long) (m.num("Przesunięcie tekstu (s)") * 1000);
            cur = Spotify.lineAt(lp, 0);
        }
        if (cur == null || cur.isEmpty()) {
            if (!editing) return null;
            cur = "Tutaj pojawi się tekst piosenki";
        }
        String t = tr.trimToWidth(cur, 320);
        int w = tr.getWidth(t) + 22;
        int x = ax - w / 2;
        Glass.panel(ctx, x, y, w, 18, 8);
        ctx.fill(x + 7, y + 6, x + 9, y + 12, 0xFF1ED760);
        ctx.drawText(tr, t, x + 13, y + 5, 0xFFFFFFFF, true);
        return new int[]{w, 18};
    }

    private static int[] modList(DrawContext ctx, TextRenderer tr, int x, int y) {
        List<String> names = new ArrayList<>();
        for (Category c : new Category[]{Category.VISUAL, Category.UTILITY}) {
            for (Module m : ModuleManager.inCategory(c)) {
                if (m.enabled) names.add(m.name);
            }
        }
        if (ModuleManager.on("spotify")) names.add("Spotify");
        if (names.isEmpty()) return null;
        String title = "Moduły";
        int w = tr.getWidth(title) + 16;
        for (String n : names) w = Math.max(w, tr.getWidth(n) + 28);
        int h = 20 + names.size() * 11 + 3;
        Glass.panel(ctx, x, y, w, h, 8);
        ctx.drawText(tr, title, x + 8, y + 6, 0xFFFFFFFF, true);
        ctx.fill(x + 8, y + 16, x + 8 + tr.getWidth(title), y + 17, Glass.accent());
        for (int i = 0; i < names.size(); i++) {
            int ly = y + 21 + i * 11;
            ctx.fill(x + 8, ly + 2, x + 11, ly + 5, Glass.accent());
            ctx.drawText(tr, names.get(i), x + 16, ly, 0xFFE4F0FF, false);
        }
        return new int[]{w, h};
    }

    private static int[] stats(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p, TextRenderer tr, int x, int y) {
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
        if (l.isEmpty()) return null;
        int w = 0;
        for (String s : l) w = Math.max(w, tr.getWidth(s));
        w += 18;
        int h = l.size() * 11 + 7;
        Glass.panel(ctx, x, y, w, h, 8);
        ctx.fill(x + 5, y + 6, x + 7, y + h - 6, Glass.accent());
        for (int i = 0; i < l.size(); i++) {
            ctx.drawText(tr, l.get(i), x + 11, y + 5 + i * 11, 0xFFFFFFFF, true);
        }
        return new int[]{w, h};
    }

    private static int[] keystrokes(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int x, int y) {
        key(ctx, tr, x + 26, y, 24, 24, "W", mc.options.forwardKey.isPressed());
        key(ctx, tr, x, y + 26, 24, 24, "A", mc.options.leftKey.isPressed());
        key(ctx, tr, x + 26, y + 26, 24, 24, "S", mc.options.backKey.isPressed());
        key(ctx, tr, x + 52, y + 26, 24, 24, "D", mc.options.rightKey.isPressed());
        key(ctx, tr, x, y + 52, 37, 22, "LPM " + cps(LEFT), prevL);
        key(ctx, tr, x + 39, y + 52, 37, 22, "PPM " + cps(RIGHT), prevR);
        key(ctx, tr, x, y + 76, 76, 14, "SPACJA", mc.options.jumpKey.isPressed());
        return new int[]{76, 90};
    }

    private static void key(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, boolean down) {
        if (down) Glass.panelAccent(ctx, x, y, w, h, 5, Glass.accent());
        else Glass.panel(ctx, x, y, w, h, 5);
        String t = tr.getWidth(label) > w - 2 ? label.substring(0, Math.min(3, label.length())) : label;
        ctx.drawText(tr, t, x + (w - tr.getWidth(t)) / 2, y + (h - 8) / 2 + 1, down ? 0xFFFFFFFF : 0xFFE0EEFF, true);
    }

    private static String mmss(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static int[] spotifyCard(DrawContext ctx, TextRenderer tr, int ax, int y) {
        boolean live = ModuleManager.on("spotify") && Spotify.running && !Spotify.title.isEmpty();
        if (!live && !editing) return null;
        String title = live ? Spotify.title : "Przykładowy utwór";
        String artist = live ? Spotify.artist : "Wykonawca";
        long pos = live ? Spotify.positionMs() : 83000;
        double dur = live ? Spotify.durationSec : 190;
        boolean playing = !live || Spotify.playing;
        int w = 216, h = 66;
        int x = ax - w;
        int green = 0xFF1ED760;
        Glass.panel(ctx, x, y, w, h, 11);

        // okładka albumu (albo zielona ikonka gdy jej nie ma)
        int cx = x + 9, cy = y + 9, cs = 48;
        Identifier cover = live ? SpotifyArt.get() : null;
        if (cover != null) {
            ctx.drawTexture(cover, cx, cy, cs, cs, 0f, 0f, 128, 128, 128, 128);
        } else {
            Glass.roundRectV(ctx, cx, cy, cs, cs, 8, 0xFF2BE57A, 0xFF138A44);
            ctx.drawText(tr, "♪", cx + cs / 2 - 3, cy + cs / 2 - 4, 0xFF04120A, false);
        }
        Glass.outline(ctx, cx - 1, cy - 1, cs + 2, cs + 2, 3, 0xAAFFFFFF, 0x44FFFFFF);

        int tx = x + 66;
        int maxW = w - 66 - 34;
        ctx.drawText(tr, Text.literal(tr.trimToWidth(title, maxW)).formatted(Formatting.BOLD), tx, y + 10, 0xFFFFFFFF, true);
        ctx.drawText(tr, tr.trimToWidth(artist, maxW), tx, y + 22, 0xFFC9DCF0, false);

        // animowany equalizer
        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < 4; i++) {
            int bh = playing ? 3 + (int) ((Math.sin(t * (5 + i * 1.7) + i * 2) * 0.5 + 0.5) * 11) : 3;
            ctx.fill(x + w - 26 + i * 4, y + 22 - bh, x + w - 24 + i * 4, y + 22, green);
        }

        // pasek postępu z gałką
        int bx = tx, bw = w - 66 - 10, by = y + 41;
        ctx.fill(bx, by, bx + bw, by + 3, 0x55FFFFFF);
        if (dur > 1) {
            double f = Math.min(1.0, pos / (dur * 1000.0));
            int fx = bx + (int) (bw * f);
            ctx.fill(bx, by, fx, by + 3, green);
            Glass.roundRect(ctx, fx - 3, by - 2, 7, 7, 3, 0xFFFFFFFF);
            String total = mmss((long) (dur * 1000));
            ctx.drawText(tr, total, bx + bw - tr.getWidth(total), by + 8, 0xFFC9DCF0, false);
        }
        ctx.drawText(tr, mmss(pos), bx, by + 8, 0xFFC9DCF0, false);
        if (!playing) ctx.drawText(tr, "||", bx + bw / 2 - 3, by + 8, 0xFFFFFFFF, false);
        return new int[]{w, h};
    }

    private static int[] effects(DrawContext ctx, ClientPlayerEntity p, TextRenderer tr, int ax, int y) {
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
        if (lines.isEmpty()) {
            if (!editing) return null;
            lines.add("Szybkość II  1:30");
            good.add(true);
        }
        String title = "Efekty";
        int w = tr.getWidth(title) + 16;
        for (String l : lines) w = Math.max(w, tr.getWidth(l) + 28);
        int h = 20 + lines.size() * 11 + 3;
        int x = ax - w;
        Glass.panel(ctx, x, y, w, h, 8);
        ctx.drawText(tr, title, x + 8, y + 6, 0xFFFFFFFF, true);
        ctx.fill(x + 8, y + 16, x + 8 + tr.getWidth(title), y + 17, Glass.accent());
        for (int i = 0; i < lines.size(); i++) {
            int ly = y + 21 + i * 11;
            ctx.fill(x + 8, ly + 2, x + 11, ly + 5, good.get(i) ? 0xFF4CE08A : 0xFFFF5A5A);
            ctx.drawText(tr, lines.get(i), x + 16, ly, 0xFFE4F0FF, false);
        }
        return new int[]{w, h};
    }

    /** Pancerz: rząd 4 slotów w szklanym panelu obok hotbara (x,y w prawdziwych współrzędnych GUI). */
    private static int[] armor(DrawContext ctx, MinecraftClient mc, PlayerEntity p, int x, int y) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        Glass.panel(ctx, x, y, 4 * 20 + 4, 22, 6);
        for (int i = 0; i < slots.length; i++) {
            ItemStack s = p.getEquippedStack(slots[i]);
            if (s.isEmpty()) continue;
            ctx.drawItem(s, x + 4 + i * 20, y + 3);
            ctx.drawItemInSlot(mc.textRenderer, s, x + 4 + i * 20, y + 3);
        }
        return new int[]{84, 22};
    }

    private static final String[] HEART = {
            ".XX...XX.",
            "XXXX.XXXX",
            "XXXXXXXXX",
            "XXXXXXXXX",
            ".XXXXXXX.",
            "..XXXXX..",
            "...XXX...",
            "....X...."};

    /** Serduszko 9x8: fill 0 = puste, 1 = połówka, 2 = pełne. */
    private static void heart(DrawContext c, int x, int y, int fill, boolean gold) {
        int main = gold ? 0xFFFFC94A : 0xFFFF3B4D;
        int hi = gold ? 0xFFFFF0B0 : 0xFFFFA3AD;
        for (int r = 0; r < HEART.length; r++) {
            for (int col = 0; col < 9; col++) {
                if (HEART[r].charAt(col) != 'X') continue;
                boolean filled = fill == 2 || (fill == 1 && col <= 4);
                int color = filled ? ((r <= 1 && col <= 2) ? hi : main) : 0x88150A12;
                c.fill(x + col, y + r, x + col + 1, y + r + 1, color);
            }
        }
    }

    private static int[] targetHud(DrawContext ctx, TextRenderer tr, int ax, int y, PlayerEntity t, ClientPlayerEntity self) {
        int w = 208;
        float hp = t.getHealth();
        float max = Math.max(1f, t.getMaxHealth());
        float abs = t.getAbsorptionAmount();
        int hearts = Math.min(20, (int) Math.ceil(max / 2f));
        int goldHearts = Math.min(10, (int) Math.ceil(abs / 2f));
        int total = hearts + goldHearts;
        int rows = Math.max(1, (total + 9) / 10);
        int headerH = Math.max(40, 18 + rows * 12);
        int h = 8 + headerH + 6 + 28 + 8;
        int x = ax - w / 2;

        Glass.roundRect(ctx, x - 2, y - 2, w + 4, h + 4, 12, 0x66000000); // ciemne tło dla czytelności
        Glass.panelAccent(ctx, x, y, w, h, 10, 0xFFFF5A7A);

        // głowa gracza ze skina
        int hx = x + 8, hy = y + 8;
        Identifier skin = null;
        if (t instanceof AbstractClientPlayerEntity ap) skin = ap.getSkinTextures().texture();
        Glass.roundRect(ctx, hx - 1, hy - 1, 42, 42, 4, 0xFF0B0F1A);
        if (skin != null) {
            ctx.drawTexture(skin, hx, hy, 40, 40, 8f, 8f, 8, 8, 64, 64);
            ctx.drawTexture(skin, hx, hy, 40, 40, 40f, 8f, 8, 8, 64, 64);
        }
        Glass.outline(ctx, hx - 1, hy - 1, 42, 42, 4, 0xCCFFFFFF, 0x55FFFFFF);

        // nick, dystans i liczba HP
        int tx = x + 56;
        ctx.drawText(tr, Text.literal(tr.trimToWidth(t.getName().getString(), w - 56 - 50)).formatted(Formatting.BOLD),
                tx, y + 9, 0xFFFFFFFF, true);
        String dist = String.format("%.1fm", (double) self.distanceTo(t));
        ctx.drawText(tr, dist, x + w - 8 - tr.getWidth(dist), y + 9, 0xFFFFC9D2, true);

        // serduszka (2 HP = 1 serce), złote = absorpcja
        int hxs = tx;
        int hyr = y + 22;
        for (int i = 0; i < total; i++) {
            boolean gold = i >= hearts;
            int fill;
            if (gold) {
                float g = abs - (i - hearts) * 2f;
                fill = g >= 2f ? 2 : g >= 1f ? 1 : 0;
            } else {
                float g = hp - i * 2f;
                fill = g >= 2f ? 2 : g >= 1f ? 1 : 0;
            }
            heart(ctx, hxs + (i % 10) * 12, hyr + (i / 10) * 12, fill, gold);
        }
        String hpText = String.format("%.1f / %.0f", hp, max) + (abs > 0 ? String.format(" +%.1f", abs) : "");
        ctx.drawText(tr, hpText, tx, hyr + rows * 12 + 1, 0xFFFFFFFF, false);

        // ekwipunek: 6 dużych slotów
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        int sy = y + 8 + headerH + 6;
        for (int i = 0; i < slots.length; i++) {
            ItemStack st = t.getEquippedStack(slots[i]);
            int sx = x + 8 + i * 32;
            Glass.roundRectV(ctx, sx, sy, 28, 28, 5, 0x88000000, 0x66000000);
            Glass.outline(ctx, sx, sy, 28, 28, 5, 0x88FFFFFF, 0x33FFFFFF);
            if (st.isEmpty()) continue;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sx + 3f, sy + 3f, 0f);
            ctx.getMatrices().scale(1.3f, 1.3f, 1f);
            ctx.drawItem(st, 0, 0);
            ctx.getMatrices().pop();
            if (st.isDamageable()) {
                float f = (st.getMaxDamage() - st.getDamage()) / (float) st.getMaxDamage();
                ctx.fill(sx + 3, sy + 25, sx + 25, sy + 27, 0xAA000000);
                ctx.fill(sx + 3, sy + 25, sx + 3 + (int) (22 * f), sy + 27, Glass.hsb(f * 0.33f, 0.85f, 1f, 255));
            } else if (st.getCount() > 1) {
                String cnt = String.valueOf(st.getCount());
                ctx.drawText(tr, cnt, sx + 26 - tr.getWidth(cnt), sy + 18, 0xFFFFFFFF, true);
            }
        }
        return new int[]{w, h};
    }

    /** Baner "ZABITO" z animacją wskoczenia. */
    private static int[] killBanner(DrawContext ctx, TextRenderer tr, int ax, int y) {
        boolean live = KillFx.bannerActive();
        String name = live ? KillFx.bannerName : "Przykładowy gracz";
        int streak = live ? KillFx.streak : 2;
        double age = live ? (System.currentTimeMillis() - KillFx.bannerStart) / 1000.0 : 1.0;
        int w = Math.max(130, tr.getWidth(name) + 56), h = 34;
        int x = ax - w / 2;

        float k = 1f;
        if (age < 0.28) {
            double f = age / 0.28;
            k = (float) (1.45 - 0.45 * (1 - Math.pow(1 - f, 3)));
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(ax, y + h / 2f, 0f);
        ctx.getMatrices().scale(k, k, 1f);
        ctx.getMatrices().translate(-ax, -(y + h / 2f), 0f);
        Glass.panelAccent(ctx, x, y, w, h, 10, Glass.accent());
        String head = "Z A B I T O";
        ctx.drawText(tr, head, ax - tr.getWidth(head) / 2, y + 6, Glass.accentLight(), true);
        ctx.drawText(tr, name, ax - tr.getWidth(name) / 2, y + 19, 0xFFFFFFFF, true);
        if (streak > 1) {
            String sx = "x" + streak;
            ctx.drawText(tr, sx, x + w - tr.getWidth(sx) - 8, y + 6, 0xFFFFD166, true);
        }
        ctx.getMatrices().pop();
        return new int[]{w, h};
    }
}
