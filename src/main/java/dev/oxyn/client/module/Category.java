package dev.oxyn.client.module;

public enum Category {
    HUD("HUD"),
    VISUAL("Efekty"),
    UTILITY("Narzędzia");

    public final String label;

    Category(String label) {
        this.label = label;
    }
}
