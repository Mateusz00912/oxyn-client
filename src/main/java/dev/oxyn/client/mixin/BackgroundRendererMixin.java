package dev.oxyn.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.oxyn.client.module.Module;
import dev.oxyn.client.module.ModuleManager;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.world.level.material.CameraSubmersionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Własna mgła terenu (gęstość i kolor) - moduł "Mgła". */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {
    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void oxyn$fog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
                                 boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (fogType != BackgroundRenderer.FogType.FOG_TERRAIN || !ModuleManager.on("fog")) return;
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        Module m = ModuleManager.get("fog");
        float density = (float) m.num("Gęstość");
        float end = Math.max(16f, viewDistance * density);
        RenderSystem.setShaderFogStart(end * 0.25f);
        RenderSystem.setShaderFogEnd(end);
        int rgb = java.awt.Color.HSBtoRGB((float) (m.num("Kolor (hue)") / 360.0), 0.55f, 0.95f);
        RenderSystem.setShaderFogColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }
}
