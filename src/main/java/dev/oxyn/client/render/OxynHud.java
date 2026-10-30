package dev.oxyn.client.render;

import dev.oxyn.client.feature.Spotify;
import dev.oxyn.client.feature.Targeting;
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
import java.util.Deque;

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
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        pollMouse(mc);

        if (ModuleManager.on("glasspanel")) topBar(ctx, mc, tr, sw);

        // Lewa kolumna pigułek
        int y = 6;
        if (ModuleManager.on("cps")) y = pill(ctx, tr, 6, y, "CPS  " + cps(LEFT) + " | " + cps(RIGHT));
        if (ModuleManager.on("ping")) {
            PlayerListEntry e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            y = pill(ctx, tr, 6, y, "Ping  " + (e == null ? "-" : e.getLatency() + " ms"));
        }
        if (ModuleManager.on("coords")) {
            y = pill(ctx, tr, 6, y, String.format("XYZ  %.0f  %.0f  %.0f", p.getX(), p.getY(), p.getZ()));
        }
        if (ModuleManager.on("direction")) {
            String d = switch (p.getHorizontalFacing()) {
                case NORTH -> "N  (-Z)";
                case SOUTH -> "S  (+Z)";
                case WEST -> "W  (-X)";
                default -> "E  (+X)";
            };
            y = pill(ctx, tr, 6, y, "Kierunek  " + d);
        }
        if (ModuleManager.on("speed")) {
            double v = Math.hypot(p.getX() - p.prevX, p.getZ() - p.prevZ) * 20.0;
            y = pill(ctx, tr, 6, y, String.format("Prędkość  %.1f b/s", v));
        }
        if (ModuleManager.on("memory")) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
            y = pill(ctx, tr, 6, y, "RAM  " + used + " / " + (rt.maxMemory() / 1048576L) + " MB");
        }
        if (ModuleManager.on("clock")) y = pill(ctx, tr, 6, y, LocalTime.now().format(CLOCK));

        if (ModuleManager.on("keystrokes")) keystrokes(ctx, mc, tr, 6, sh - 98);

        // Prawa kolumna
        int ry = 6;
        if (ModuleManager.on("spotify")) ry += spotify(ctx, tr, sw, ry) + 6;
        if (ModuleManager.on("effects")) effects(ctx, p, tr, sw, ry);

        if (ModuleManager.on("armor")) armor(ctx, p, tr, sw - 70, sh - 100);
        if (ModuleManager.on("targethud") && Targeting.target != null) targetHud(ctx, tr, sw, sh, Targeting.target, p);
    }

    private static int pill(DrawContext ctx, TextRenderer tr, int x, int y, String text) {
        int w = tr.getWidth(text) + 14;
        Glass.panel(ctx, x, y, w, 18, 6);
        ctx.fill(x + 4, y + 5, x + 6, y + 13, Glass.ACCENT);
        ctx.drawText(tr, text, x + 9, y + 5, 0xFFFFFFFF, true);
        return y + 22;
    }

    private static void topBar(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int sw) {
        String server = mc.isInSingleplayer() ? "Singleplayer"
                : mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Offline";
        String fps = mc.getCurrentFps() + " FPS";
        String name = "OXYN CLIENT";
        int pad = 12, gap = 14;
        int w = pad + tr.getWidth(name) + gap + tr.getWidth(fps) + gap + tr.getWidth(server) + pad;
        int x = (sw - w) / 2;
        int y = 5;
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
        ctx.drawText(tr, t, x + (w - tr.getWidth(t)) / 2, y + (h - 8) / 2 + 1, down ? 0xFFFFFFFF : 0xFFB8D4F0, true);
    }

    private static int spotify(DrawContext ctx, TextRenderer tr, int sw, int y) {
        if (!Spotify.running || Spotify.title.isEmpty()) return -6;
        Module m = ModuleManager.get("spotify");
        boolean lyr = m.flag("Tekst piosenki");
        int w = 200;
        int h = lyr ? 66 : 40;
        int x = sw - w - 6;
        Glass.panel(ctx, x, y, w, h, 9);
        // ikonka
        Glass.roundRect(ctx, x + 6, y + 6, 22, 22, 11, 0xFF1ED760);
        ctx.drawText(tr, Spotify.playing ? ">" : "||", x + 14 - (Spotify.playing ? 0 : 2), y + 13, 0xFF04120A, false);
        ctx.drawText(tr, tr.trimToWidth(Spotify.title, w - 42), x + 34, y + 7, 0xFFFFFFFF, true);
        ctx.drawText(tr, tr.trimToWidth(Spotify.artist, w - 42), x + 34, y + 18, 0xFF9FB4C8, false);

        long pos = Spotify.positionMs();
        if (Spotify.durationSec > 1) {
            double f = Math.min(1.0, pos / (Spotify.durationSec * 1000.0));
            ctx.fill(x + 8, y + 33, x + w - 8, y + 35, 0x40FFFFFF);
            ctx.fill(x + 8, y + 33, x + 8 + (int) ((w - 16) * f), y + 35, 0xFF1ED760);
        }
        if (lyr) {
            long lp = pos + (long) (m.num("Przesunięcie tekstu (s)") * 1000);
            String cur = Spotify.hasLyrics() ? Spotify.lineAt(lp, 0) : "(brak tekstu)";
            String next = Spotify.hasLyrics() ? Spotify.lineAt(lp, 1) : "";
            if (cur.isEmpty()) cur = "...";
            ctx.drawText(tr, tr.trimToWidth(cur, w - 14), x + 8, y + 41, 0xFFFFFFFF, true);
            ctx.drawText(tr, tr.trimToWidth(next, w - 14), x + 8, y + 53, 0xFF7C93AB, false);
        }
        return h;
    }

    private static void effects(DrawContext ctx, ClientPlayerEntity p, TextRenderer tr, int sw, int y) {
        for (StatusEffectInstance e : p.getStatusEffects()) {
            if (e.getDuration() > 100000) continue; // nasz efekt "jasność nocą"
            String name = e.getEffectType().value().getName().getString();
            if (e.getAmplifier() > 0) name += " " + (e.getAmplifier() + 1);
            int s = e.getDuration() / 20;
            String text = name + "  " + (s / 60) + ":" + String.format("%02d", s % 60);
            int w = tr.getWidth(text) + 14;
            int x = sw - w - 6;
            Glass.panel(ctx, x, y, w, 18, 6);
            ctx.fill(x + 4, y + 5, x + 6, y + 13, e.getEffectType().value().isBeneficial() ? 0xFF4CE08A : 0xFFFF5A5A);
            ctx.drawText(tr, text, x + 9, y + 5, 0xFFFFFFFF, true);
            y += 22;
        }
    }

    private static void armor(DrawContext ctx, PlayerEntity p, TextRenderer tr, int x, int y) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
        Glass.panel(ctx, x - 4, y - 4, 68, slots.length * 18 + 6, 7);
        for (int i = 0; i < slots.length; i++) {
            ItemStack s = p.getEquippedStack(slots[i]);
            int yy = y + i * 18;
            if (s.isEmpty()) continue;
            ctx.drawItem(s, x, yy);
            if (s.isDamageable()) {
                int left = s.getMaxDamage() - s.getDamage();
                float f = left / (float) s.getMaxDamage();
                ctx.drawText(tr, String.valueOf(left), x + 20, yy + 4, Glass.hsb(f * 0.33f, 0.8f, 1f, 255), true);
            } else if (s.getCount() > 1) {
                ctx.drawText(tr, "x" + s.getCount(), x + 20, yy + 4, 0xFFFFFFFF, true);
            }
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
