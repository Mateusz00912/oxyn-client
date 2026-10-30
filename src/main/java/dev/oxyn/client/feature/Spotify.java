package dev.oxyn.client.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
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
 * Windows - tytuł okna Spotify.exe ("Artysta - Utwór"), Linux - playerctl.
 * Tekst piosenki pobierany z lrclib.net (synchronizowany .lrc).
 * Pozycja utworu jest szacowana od momentu zmiany tytułu (z uwzględnieniem pauzy).
 */
public final class Spotify {
    public static volatile String title = "";
    public static volatile String artist = "";
    public static volatile boolean running = false;
    public static volatile boolean playing = false;
    public static volatile double durationSec = 0;
    private static volatile TreeMap<Long, String> lyrics = new TreeMap<>();

    private static String lastKey = "";
    private static long accumulatedMs = 0;
    private static long resumedAt = 0;
    private static boolean started = false;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

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
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void poll() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        String a = "";
        String t = "";
        boolean isRunning = false;
        boolean isPlaying = false;

        if (os.contains("win")) {
            String winTitle = windowTitle();
            if (winTitle != null) {
                isRunning = true;
                int i = winTitle.indexOf(" - ");
                if (i > 0) {
                    a = winTitle.substring(0, i).trim();
                    t = winTitle.substring(i + 3).trim();
                    isPlaying = true;
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
            fetchLyrics(a, t);
        } else if (isPlaying && !playing) {
            resumedAt = System.currentTimeMillis();
            playing = true;
        } else if (!isPlaying && playing) {
            accumulatedMs = positionMs();
            playing = false;
        }
    }

    /** Zwraca tytuł okna Spotify z utworem albo null gdy Spotify nie działa. */
    private static String windowTitle() throws Exception {
        String out = exec("tasklist", "/v", "/fo", "csv", "/nh", "/fi", "IMAGENAME eq Spotify.exe");
        if (out == null) return null;
        boolean found = false;
        String idle = null;
        for (String line : out.split("\\r?\\n")) {
            if (!line.toLowerCase().contains("spotify.exe")) continue;
            found = true;
            String[] cols = line.split("\",\"");
            String wt = cols[cols.length - 1].replace("\"", "").trim();
            if (wt.isEmpty() || wt.equalsIgnoreCase("N/A") || wt.equalsIgnoreCase("N/D")) continue;
            if (wt.startsWith("Spotify") || wt.equals("Spotify Free") || wt.equals("Spotify Premium")) {
                idle = wt;
                continue;
            }
            if (wt.contains(" - ")) return wt;
        }
        if (!found) return null;
        return idle != null ? idle : "Spotify";
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

    private static void fetchLyrics(String a, String t) {
        Thread th = new Thread(() -> {
            try {
                String url = "https://lrclib.net/api/get?artist_name=" + enc(a) + "&track_name=" + enc(t);
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "OxynClient/1.0")
                        .timeout(Duration.ofSeconds(8)).GET().build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) return;
                JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                if (!(a + "|" + t).equals(lastKey)) return;
                if (o.has("duration") && !o.get("duration").isJsonNull()) durationSec = o.get("duration").getAsDouble();
                if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) {
                    lyrics = parseLrc(o.get("syncedLyrics").getAsString());
                }
            } catch (Exception ignored) {
            }
        }, "Oxyn-Lyrics");
        th.setDaemon(true);
        th.start();
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
