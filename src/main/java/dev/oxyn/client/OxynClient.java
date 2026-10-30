package dev.oxyn.client;

import dev.oxyn.client.feature.KillFx;
import dev.oxyn.client.feature.Spotify;
import dev.oxyn.client.feature.Targeting;
import dev.oxyn.client.gui.OxynMenuScreen;
import dev.oxyn.client.gui.OxynTitleScreen;
import dev.oxyn.client.module.Module;
import dev.oxyn.client.module.ModuleManager;
import dev.oxyn.client.render.OxynHud;
import dev.oxyn.client.render.WorldFx;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.util.ActionResult;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import org.lwjgl.glfw.GLFW;

public class OxynClient implements ClientModInitializer {
    public static KeyBinding menuKey;
    public static KeyBinding zoomKey;
    private static boolean zooming;
    private static int savedFov;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        Spotify.start();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.oxyn.menu",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "key.category.oxyn"));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.oxyn.zoom",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.category.oxyn"));

        HudRenderCallback.EVENT.register(OxynHud::render);
        WorldRenderEvents.AFTER_ENTITIES.register(WorldFx::renderTags);
        WorldRenderEvents.LAST.register(WorldFx::renderShapes);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient()) KillFx.onAttack(entity);
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(OxynClient::tick);
    }

    private static void tick(MinecraftClient mc) {
        // Własne menu główne zamiast vanillowego
        if (ModuleManager.customTitle && mc.currentScreen != null && mc.currentScreen.getClass() == TitleScreen.class) {
            mc.setScreen(new OxynTitleScreen());
        }

        while (menuKey.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new OxynMenuScreen(null));
        }

        Targeting.tick(mc);
        KillFx.tick(mc);
        WorldFx.tick(mc);

        ClientPlayerEntity p = mc.player;
        if (p == null) return;

        // Auto-sprint
        if (ModuleManager.on("sprint") && p.input.movementForward > 0.8f && !p.isSneaking()
                && !p.horizontalCollision && !p.isUsingItem() && p.getHungerManager().getFoodLevel() > 6) {
            p.setSprinting(true);
        }

        // Jasność nocą (efekt kliencki)
        boolean has = p.hasStatusEffect(StatusEffects.NIGHT_VISION);
        if (ModuleManager.on("fullbright")) {
            if (!has) p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1_000_000, 0, false, false, false));
        } else if (has) {
            StatusEffectInstance e = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (e != null && e.getDuration() > 100_000) p.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }

        // Zoom (przytrzymaj C)
        Module z = ModuleManager.get("zoom");
        boolean want = z.enabled && zoomKey.isPressed() && mc.currentScreen == null;
        if (want) {
            if (!zooming) {
                savedFov = mc.options.getFov().getValue();
                zooming = true;
            }
            mc.options.getFov().setValue((int) z.num("FOV"));
        } else if (zooming) {
            mc.options.getFov().setValue(savedFov);
            zooming = false;
        }
    }
}
