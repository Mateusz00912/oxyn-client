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

    private ModuleManager() {}

    public static void init() {
        // HUD
        add(new Module("hudlayout", "Układ HUD", "Skala HUD (1 = auto) i wysokość panelu górnego", Category.HUD, true)
                .slider("Skala", 0.5, 1.5, 1.0)
                .slider("Panel górny Y", 0, 80, 24));
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
                .toggle("Tekst piosenki", true)
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
