package dev.oxyn.client.render;

import dev.oxyn.client.feature.Spotify;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;

/** Rejestruje okładkę albumu jako teksturę (w wątku renderowania). */
public final class SpotifyArt {
    private static final Identifier ID = Identifier.of("oxyn", "spotify_art");
    private static int version = -1;
    private static boolean has = false;

    private SpotifyArt() {}

    /** Zwraca identyfikator tekstury okładki albo null gdy jej nie ma. */
    public static Identifier get() {
        int v = Spotify.artVersion;
        if (v != version) {
            version = v;
            has = false;
            byte[] b = Spotify.artPng;
            if (b != null) {
                try {
                    NativeImage img = NativeImage.read(new ByteArrayInputStream(b));
                    MinecraftClient.getInstance().getTextureManager().registerTexture(ID, new NativeImageBackedTexture(img));
                    has = true;
                } catch (Exception ignored) {
                }
            }
        }
        return has ? ID : null;
    }
}
