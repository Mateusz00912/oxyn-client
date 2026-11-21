package dev.oxyn.client.module;

public enum Category {
    HUD("HUD"),
    VISUAL("Efekty"),
    MOVEMENT("Ruch"),
    COMBAT("Walka"),
    WORLD("Świat"),
    UTILITY("Narzędzia");

    public final String label;

    Category(String label) {
        this.label = label;
    }
}
