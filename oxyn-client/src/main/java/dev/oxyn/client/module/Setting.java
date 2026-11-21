package dev.oxyn.client.module;

public class Setting {
    public final String name;
    public final double min;
    public final double max;
    public final boolean toggle;
    public double value;

    /** Suwak. */
    public Setting(String name, double min, double max, double def) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.value = def;
        this.toggle = false;
    }

    /** Przełącznik tak/nie. */
    public Setting(String name, boolean def) {
        this.name = name;
        this.min = 0;
        this.max = 1;
        this.value = def ? 1 : 0;
        this.toggle = true;
    }

    public boolean on() {
        return value >= 0.5;
    }

    public void set(double v) {
        this.value = Math.max(min, Math.min(max, v));
    }
}
