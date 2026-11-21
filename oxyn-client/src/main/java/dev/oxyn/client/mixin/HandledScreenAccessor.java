package dev.oxyn.client.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Dostęp do położenia i rozmiaru okna ekwipunku. */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int oxyn$getX();

    @Accessor("y")
    int oxyn$getY();

    @Accessor("backgroundWidth")
    int oxyn$getW();

    @Accessor("backgroundHeight")
    int oxyn$getH();
}
