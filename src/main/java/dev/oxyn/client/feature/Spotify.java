package dev.oxyn.client.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

/**
 * Odczyt aktualnego utworu ze Spotify bez logowania:
 * Windows - tytuł okna aplikacji Spotify (PowerShell, UTF-8), Linux - playerctl.
 * Tekst piosenki: lrclib.net, okładka i długość: iTunes Search API (publiczne, bez klucza).
 * Pozycja utworu jest szacowana od momentu zmiany tytułu (z uwzględnieniem pauzy).
 */
public final class Spotify {
    public static volatile String title = "";
    public static volatile String artist = "";
    public static volatile boolean running = false;
    public static volatile boolean playing = false;
    public static volatile double durationSec = 0;
    public static volatile byte[] artPng = null;
    public static volatile int artVersion = 0;
    private static volatile TreeMap<Long, String> lyrics = new TreeMap<>();

    private static String lastKey = "";
    private static long accumulatedMs = 0;
    private static long resumedAt = 0;
    private static boolean started = false;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static final String PS = "[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false; "
            + "$p = Get-Process Spotify -ErrorAction SilentlyContinue; "
            + "if (-not $p) { 'NONE' } else { "
            + "$t = ($p | Where-Object { $_.MainWindowTitle } | Select-Object -First 1).MainWindowTitle; "
            + "if ($t) { $t } else { 'IDLE' } }";

    private Spotify() {}

    public static synchronized void start() {
        if (started) return;
        started = true;
        Thread t = new Thread(Spotify::loop, "Oxyn-Spotify");
        t.setDaemon(true);
        t.start();
    }

    public static long positionMs() {
        long pos = accumulatedMs + (playing ? System.currentTimeMillis() - resumedAt : 0);
        return Math.max(0, pos);
    }

    public static String lineAt(long ms, int offset) {
        TreeMap<Long, String> l = lyrics;
        if (l.isEmpty()) return "";
        Map.Entry<Long, String> e = l.floorEntry(ms);
        if (e == null) return offset > 0 ? l.firstEntry().getValue() : "";
        if (offset == 0) return e.getValue();
        Map.Entry<Long, String> n = e;
        for (int i = 0; i < offset && n != null; i++) n = l.higherEntry(n.getKey());
        return n == null ? "" : n.getValue();
    }

    /** {początek linii, koniec linii} w ms dla pozycji ms, albo null gdy jeszcze nie zaczął się tekst. */
    public static long[] span(long ms) {
        TreeMap<Long, String> l = lyrics;
        Map.Entry<Long, String> e = l.floorEntry(ms);
        if (e == null) return null;
        Long next = l.higherKey(e.getKey());
        return new long[]{e.getKey(), next == null ? e.getKey() + 4000 : next};
    }

    public static boolean hasLyrics() {
        return !lyrics.isEmpty();
    }

    private static void loop() {
        while (true) {
            try {
                poll();
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void poll() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String a = "";
        String t = "";
        boolean isRunning = false;
        boolean isPlaying = false;

        if (os.contains("win")) {
            String out = exec("powershell", "-NoProfile", "-NonInteractive", "-Command", PS);
            if (out != null) {
                String w = out.replace("﻿", "").trim();
                if (!w.isEmpty() && !w.equals("NONE")) {
                    isRunning = true;
                    int i = w.indexOf(" - ");
                    boolean idle = w.equals("IDLE") || w.startsWith("Spotify");
                    if (!idle && i > 0) {
                        a = w.substring(0, i).trim();
                        t = w.substring(i + 3).trim();
                        isPlaying = true;
                    }
                }
            }
        } else if (os.contains("linux")) {
            String out = exec("playerctl", "-p", "spotify", "metadata", "--format", "{{artist}}\t{{title}}\t{{status}}");
            if (out != null && out.contains("\t")) {
                String[] p = out.trim().split("\t");
                if (p.length >= 3) {
                    isRunning = true;
                    a = p[0];
                    t = p[1];
                    isPlaying = p[2].equalsIgnoreCase("Playing");
                }
            }
        }

        running = isRunning;
        String key = a + "|" + t;
        if (!isRunning || t.isEmpty()) {
            if (playing) {
                accumulatedMs = positionMs();
                playing = false;
            }
            if (!isRunning) {
                title = "";
                artist = "";
            }
            return;
        }

        if (!key.equals(lastKey)) {
            lastKey = key;
            title = t;
            artist = a;
            accumulatedMs = 0;
            resumedAt = System.currentTimeMillis();
            playing = isPlaying;
            durationSec = 0;
            lyrics = new TreeMap<>();
            artPng = null;
            artVersion++;
            fetchMeta(a, t);
        } else if (isPlaying && !playing) {
            resumedAt = System.currentTimeMillis();
            playing = true;
        } else if (!isPlaying && playing) {
            accumulatedMs = positionMs();
            playing = false;
        }
    }

    private static String exec(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l).append('\n');
            }
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void fetchMeta(String a, String t) {
        Thread th = new Thread(() -> {
            String key = a + "|" + t;
            // 1) tekst piosenki (lrclib)
            try {
                String url = "https://lrclib.net/api/get?artist_name=" + enc(a) + "&track_name=" + enc(t);
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "OxynClient/1.0").timeout(Duration.ofSeconds(8)).GET().build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200 && key.equals(lastKey)) {
                    JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                    if (o.has("duration") && !o.get("duration").isJsonNull()) durationSec = o.get("duration").getAsDouble();
                    if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) {
                        lyrics = parseLrc(o.get("syncedLyrics").getAsString());
                    }
                }
            } catch (Exception ignored) {
            }
            // 2) okładka i długość (iTunes)
            try {
                String url = "https://itunes.apple.com/search?media=music&entity=song&limit=5&term=" + enc(a + " " + t);
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "OxynClient/1.0").timeout(Duration.ofSeconds(8)).GET().build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200 || !key.equals(lastKey)) return;
                JsonArray arr = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("results");
                for (JsonElement el : arr) {
                    JsonObject r = el.getAsJsonObject();
                    if (!r.has("trackName") || !similar(r.get("trackName").getAsString(), t)) continue;
                    if (durationSec <= 0 && r.has("trackTimeMillis")) durationSec = r.get("trackTimeMillis").getAsDouble() / 1000.0;
                    if (r.has("artworkUrl100")) {
                        String au = r.get("artworkUrl100").getAsString().replace("100x100", "300x300");
                        HttpRequest ir = HttpRequest.newBuilder(URI.create(au)).timeout(Duration.ofSeconds(8)).GET().build();
                        HttpResponse<byte[]> ires = HTTP.send(ir, HttpResponse.BodyHandlers.ofByteArray());
                        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(ires.body()));
                        if (bi != null && key.equals(lastKey)) {
                            BufferedImage out = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                            Graphics2D g = out.createGraphics();
                            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                            g.drawImage(bi, 0, 0, 128, 128, null);
                            g.dispose();
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            ImageIO.write(out, "png", bos);
                            artPng = bos.toByteArray();
                            artVersion++;
                        }
                    }
                    break;
                }
            } catch (Exception ignored) {
            }
        }, "Oxyn-SpotifyMeta");
        th.setDaemon(true);
        th.start();
    }

    private static boolean similar(String x, String y) {
        String a = norm(x), b = norm(y);
        if (a.length() < 3 || b.length() < 3) return a.equals(b);
        return a.contains(b) || b.contains(a);
    }

    private static String norm(String s) {
        return s.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static TreeMap<Long, String> parseLrc(String lrc) {
        TreeMap<Long, String> map = new TreeMap<>();
        for (String line : lrc.split("\\r?\\n")) {
            if (line.length() < 10 || line.charAt(0) != '[') continue;
            int close = line.indexOf(']');
            if (close < 0) continue;
            try {
                String ts = line.substring(1, close);
                int colon = ts.indexOf(':');
                int min = Integer.parseInt(ts.substring(0, colon));
                double sec = Double.parseDouble(ts.substring(colon + 1));
                String text = line.substring(close + 1).trim();
                map.put((long) ((min * 60 + sec) * 1000), text);
            } catch (Exception ignored) {
            }
        }
        return map;
    }
}
