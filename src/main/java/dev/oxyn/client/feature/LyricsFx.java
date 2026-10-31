package dev.oxyn.client.feature;

import dev.oxyn.client.module.Module;
import dev.oxyn.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Tekst piosenki w powietrzu: każda linia pojawia się przed graczem, zostaje w miejscu świata
 * (nie biegnie z graczem) i znika po końcu zdania.
 */
public final class LyricsFx {
    public static final class Line {
        public final String text;
        public final Vec3d pos;
        public final long start;
        public final long end;

        Line(String text, Vec3d pos, long start, long end) {
            this.text = text;
            this.pos = pos;
            this.start = start;
            this.end = end;
        }
    }

    public static final List<Line> LINES = new ArrayList<>();
    private static long lastKey = -1;
    private static String lastTrack = "";

    private LyricsFx() {}

    public static void tick(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        LINES.removeIf(l -> now > l.end + 800);

        Module m = ModuleManager.get("spotify");
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || !m.enabled || !m.flag("Tekst w powietrzu")) return;
        if (!Spotify.running || !Spotify.playing || !Spotify.hasLyrics()) return;

        String track = Spotify.artist + "|" + Spotify.title;
        if (!track.equals(lastTrack)) {
            lastTrack = track;
            lastKey = -1;
        }

        long pos = Spotify.positionMs() + (long) (m.num("Przesunięcie tekstu (s)") * 1000);
        long[] sp = Spotify.span(pos);
        if (sp == null || sp[0] == lastKey) return;
        lastKey = sp[0];
        String text = Spotify.lineAt(pos, 0);
        if (text.isEmpty()) return;

        long remaining = Math.max(1500, sp[1] - pos);
        Vec3d eye = p.getEyePos();
        Vec3d dir = Vec3d.fromPolar(0f, p.getYaw()); // poziomy kierunek patrzenia
        Vec3d target = eye.add(dir.multiply(6.0)).add(0, 0.25, 0);
        HitResult hit = mc.world.raycast(new RaycastContext(eye, target,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        if (hit.getType() != HitResult.Type.MISS) {
            target = hit.getPos().subtract(dir.multiply(0.6));
        }
        // poprzednia linia kończy się razem z nową
        for (Line l : LINES) {
            if (l.end > now) {
                LINES.set(LINES.indexOf(l), new Line(l.text, l.pos, l.start, now));
                break;
            }
        }
        LINES.add(new Line(text, target, now, now + remaining));
        while (LINES.size() > 6) LINES.remove(0);
    }
}
