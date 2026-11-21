package dev.oxyn.client.module;

import java.util.ArrayList;
import java.util.List;

public class Module {
    public final String id;
    public final String name;
    public final String desc;
    public final Category category;
    public final List<Setting> settings = new ArrayList<>();
    public boolean enabled;
    public boolean expanded;

    public Module(String id, String name, String desc, Category category, boolean enabled) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.category = category;
        this.enabled = enabled;
    }

    public Module slider(String n, double min, double max, double def) {
        settings.add(new Setting(n, min, max, def));
        return this;
    }

    public Module toggle(String n, boolean def) {
        settings.add(new Setting(n, def));
        return this;
    }

    public Setting setting(String n) {
        for (Setting s : settings) {
            if (s.name.equals(n)) return s;
        }
        throw new IllegalArgumentException("Brak ustawienia " + n + " w " + id);
    }

    public double num(String n) {
        return setting(n).value;
    }

    public boolean flag(String n) {
        return setting(n).on();
    }
}
