package dev.oxyn.client.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private static final Map<String, Module> MODULES = new LinkedHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Ustawienia ogólne (edytowalne w oxyn.json). */
    public static boolean customTitle = true;
    public static String discordUrl = "https://discord.gg/";
    public static String storeUrl = "https://modrinth.com/mods";

    /** Układ elementów HUD: id -> {przesunięcie X, przesunięcie Y, skala}. */
    private static final Map<String, double[]> HUD = new LinkedHashMap<>();

    private ModuleManager() {}

    private static double[] defaults(String id) {
        return switch (id) {
            case "topbar", "lyric" -> new double[]{0, 24, 1};
            default -> new double[]{0, 0, 1};
        };
    }

    public static double[] hud(String id) {
        return HUD.computeIfAbsent(id, ModuleManager::defaults);
    }

    public static void resetHud(String id) {
        HUD.put(id, defaults(id));
    }

    public static void resetHudAll() {
        for (String k : new ArrayList<>(HUD.keySet())) HUD.put(k, defaults(k));
    }

    /** Gotowe style szkła: {barwa, nasycenie, jasność, krycie, obwódka, połysk, zaokrąglenie, akcent}. */
    public static void preset(String name) {
        double[] v = switch (name) {
            case "Ciemne" -> new double[]{222, 0.6, 0.14, 0.82, 0.35, 0.10, 1.0, 208};
            case "Neon" -> new double[]{265, 0.8, 0.22, 0.65, 0.95, 0.16, 1.1, 290};
            case "Kryształ" -> new double[]{200, 0.12, 0.92, 0.20, 0.85, 0.38, 1.5, 195};
            default -> new double[]{212, 0.5, 0.5, 0.5, 0.6, 0.22, 1.1, 208}; // Mleczne
        };
        Module t = MODULES.get("theme");
        for (int i = 0; i < v.length; i++) t.settings.get(i).set(v[i]);
        save();
    }

    public static void init() {
        // HUD
        add(new Module("hudlayout", "Układ HUD", "Skala HUD (1 = auto) i wysokość panelu górnego", Category.HUD, true)
                .slider("Skala", 0.5, 1.5, 1.0));
        add(new Module("theme", "Wygląd", "Kolory i szkło", null, true)
                .slider("Barwa szkła", 0, 360, 212)
                .slider("Nasycenie", 0, 1, 0.5)
                .slider("Jasność", 0, 1, 0.5)
                .slider("Krycie szkła", 0.1, 1, 0.5)
                .slider("Obwódka", 0, 1, 0.6)
                .slider("Połysk", 0, 1, 0.22)
                .slider("Zaokrąglenie", 0.3, 1.6, 1.1)
                .slider("Akcent (barwa)", 0, 360, 208));
        add(new Module("modlist", "Lista modułów", "Panel z aktywnymi modułami", Category.HUD, true));
        add(new Module("glasspanel", "Panel górny", "Liquid glass panel: nazwa, FPS, serwer", Category.HUD, true));
        add(new Module("cps", "CPS", "Kliknięcia na sekundę", Category.HUD, true));
        add(new Module("ping", "Ping", "Opóźnienie do serwera", Category.HUD, true));
        add(new Module("coords", "Koordynaty", "XYZ gracza", Category.HUD, true));
        add(new Module("direction", "Kierunek", "Strona świata", Category.HUD, true));
        add(new Module("speed", "Prędkość", "Bloki na sekundę", Category.HUD, false));
        add(new Module("memory", "Pamięć", "Użycie RAM", Category.HUD, false));
        add(new Module("clock", "Zegar", "Godzina systemowa", Category.HUD, true));
        add(new Module("keystrokes", "Keystrokes", "WASD, spacja, LPM/PPM", Category.HUD, true));
        add(new Module("armor", "Pancerz", "Pancerz i wytrzymałość", Category.HUD, true));
        add(new Module("effects", "Efekty mikstur", "Aktywne efekty", Category.HUD, true));
        add(new Module("spotify", "Spotify", "Utwór + tekst (liquid glass)", Category.HUD, true)
                .toggle("Tekst w powietrzu", true)
                .toggle("Tekst na HUD", false)
                .slider("Rozmiar tekstu", 0.015, 0.08, 0.03)
                .slider("Przesunięcie tekstu (s)", -5, 5, 0));
        add(new Module("targethud", "Target HUD", "HP i ekwipunek gracza na celowniku", Category.HUD, true)
                .slider("Zasięg (bloki)", 8, 64, 40));

        // Efekty wizualne
        add(new Module("trail", "Smuga", "Linia za Tobą gdy biegasz i skaczesz", Category.VISUAL, true)
                .slider("Długość", 8, 80, 36)
                .slider("Rozmiar", 0.1, 0.9, 0.35)
                .toggle("Tęcza", false)
                .toggle("Tylko w ruchu", true));
        add(new Module("aura", "Aura", "Świecące pierścienie wokół Ciebie", Category.VISUAL, true)
                .slider("Promień", 0.5, 1.6, 0.9)
                .slider("Prędkość", 0.2, 3.0, 1.0));
        add(new Module("targetring", "Okrąg na celu", "Okrąg wokół gracza na którego patrzysz", Category.VISUAL, true)
                .slider("Zasięg (bloki)", 8, 64, 40));
        add(new Module("hptags", "HP graczy", "HP nad głowami graczy", Category.VISUAL, true)
                .slider("Zasięg (bloki)", 8, 96, 48));
        add(new Module("killfx", "Animacja zabicia", "Fale, iskry i baner po zabiciu", Category.VISUAL, true)
                .toggle("Fale i iskry", true)
                .toggle("Baner", true)
                .toggle("Dźwięk", true)
                .slider("Czas banera (s)", 1, 5, 2.5));
        add(new Module("glassinv", "Szklany ekwipunek", "Ekwipunek i skrzynie jako rozmazane szkło", Category.VISUAL, true));
        add(new Module("butterfly", "Nóż motylkowy", "Zamiast miecza w ręku animowany balisong", Category.VISUAL, true)
                .slider("Skala", 0.5, 2.0, 1.0)
                .slider("Przesunięcie X", -0.6, 0.6, 0)
                .slider("Przesunięcie Y", -0.6, 0.6, 0)
                .slider("Przesunięcie Z", -0.6, 0.6, 0)
                .slider("Obrót", -90, 90, 0)
                .slider("Szybkość animacji", 0.3, 3.0, 1.0));
        add(new Module("ownnametag", "Nick OXYN", "Napis OXYN (nick) nad Twoją postacią", Category.VISUAL, true));
        add(new Module("fog", "Mgła", "Własny kolor i gęstość mgły", Category.VISUAL, false)
                .slider("Gęstość", 0.1, 1.0, 0.55)
                .slider("Kolor (hue)", 0, 360, 210));
        add(new Module("fullbright", "Jasność nocą", "Widzenie w ciemności (efekt kliencki)", Category.VISUAL, false));

        // Narzędzia
        add(new Module("sprint", "Auto-sprint", "Automatyczny sprint przy chodzeniu do przodu", Category.UTILITY, true));
        add(new Module("zoom", "Zoom", "Przytrzymaj klawisz C", Category.UTILITY, true)
                .slider("FOV", 30, 70, 30));

        load();
    }

    private static Module add(Module m) {
        MODULES.put(m.id, m);
        return m;
    }

    public static Module get(String id) {
        return MODULES.get(id);
    }

    public static boolean on(String id) {
        Module m = MODULES.get(id);
        return m != null && m.enabled;
    }

    public static List<Module> inCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : MODULES.values()) {
            if (m.category == c) out.add(m);
        }
        return out;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("oxyn.json");
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("customTitle", customTitle);
            root.addProperty("discordUrl", discordUrl);
            root.addProperty("storeUrl", storeUrl);
            JsonObject mods = new JsonObject();
            for (Module m : MODULES.values()) {
                JsonObject o = new JsonObject();
                o.addProperty("enabled", m.enabled);
                for (Setting s : m.settings) o.addProperty(s.name, s.value);
                mods.add(m.id, o);
            }
            root.add("modules", mods);
            JsonObject hud = new JsonObject();
            for (Map.Entry<String, double[]> e : HUD.entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("dx", e.getValue()[0]);
                o.addProperty("dy", e.getValue()[1]);
                o.addProperty("scale", e.getValue()[2]);
                hud.add(e.getKey(), o);
            }
            root.add("hud", hud);
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(root, w);
            }
        } catch (Exception e) {
            System.err.println("[Oxyn] Nie udało się zapisać konfiguracji: " + e);
        }
    }

    private static void load() {
        Path f = file();
        if (!Files.exists(f)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(f)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root.has("customTitle")) customTitle = root.get("customTitle").getAsBoolean();
            if (root.has("discordUrl")) discordUrl = root.get("discordUrl").getAsString();
            if (root.has("storeUrl")) storeUrl = root.get("storeUrl").getAsString();
            if (root.has("hud")) {
                JsonObject h = root.getAsJsonObject("hud");
                for (String k : h.keySet()) {
                    JsonObject o = h.getAsJsonObject(k);
                    double[] st = hud(k);
                    if (o.has("dx")) st[0] = o.get("dx").getAsDouble();
                    if (o.has("dy")) st[1] = o.get("dy").getAsDouble();
                    if (o.has("scale")) st[2] = Math.max(0.4, Math.min(3.0, o.get("scale").getAsDouble()));
                }
            }
            if (!root.has("modules")) return;
            JsonObject mods = root.getAsJsonObject("modules");
            for (Module m : MODULES.values()) {
                if (!mods.has(m.id)) continue;
                JsonObject o = mods.getAsJsonObject(m.id);
                if (o.has("enabled")) m.enabled = o.get("enabled").getAsBoolean();
                for (Setting s : m.settings) {
                    if (o.has(s.name)) s.set(o.get(s.name).getAsDouble());
                }
            }
        } catch (Exception e) {
            System.err.println("[Oxyn] Błąd wczytywania konfiguracji, używam domyślnej: " + e);
        }
    }
}
