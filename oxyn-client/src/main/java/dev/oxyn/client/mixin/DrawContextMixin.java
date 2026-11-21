package dev.oxyn.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.oxyn.client.module.ModuleManager;
import dev.oxyn.client.render.Glass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Szklany ekwipunek: pod tłem okna (tekstura kontenera) rysujemy panel liquid glass i szklane sloty,
 * a samą szarą teksturę przyciemniamy do ~20% krycia.
 */
@Mixin(DrawContext.class)
public class DrawContextMixin {
    @Unique
    private boolean oxyn$tinted;

    @Inject(method = "drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V", at = @At("HEAD"), require = 0)
    private void oxyn$glassBackground(Identifier texture, int x, int y, int u, int v, int width, int height, CallbackInfo ci) {
        if (!ModuleManager.on("glassinv")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof HandledScreen<?> screen) || !(mc.currentScreen instanceof HandledScreenAccessor acc)) return;
        if (!texture.getPath().startsWith("textures/gui/container/")) return;

        DrawContext self = (DrawContext) (Object) this;
        if (u == 0 && v == 0 && x == acc.oxyn$getX() && y == acc.oxyn$getY()) {
            Glass.panel(self, x, y, acc.oxyn$getW(), acc.oxyn$getH(), 8);
            for (Slot s : screen.getScreenHandler().slots) {
                if (s.isEnabled()) Glass.cell(self, x + s.x - 1, y + s.y - 1, 18, 18);
            }
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.2f);
        oxyn$tinted = true;
    }

    @Inject(method = "drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V", at = @At("RETURN"), require = 0)
    private void oxyn$resetColor(Identifier texture, int x, int y, int u, int v, int width, int height, CallbackInfo ci) {
        if (oxyn$tinted) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            oxyn$tinted = false;
        }
    }
}
