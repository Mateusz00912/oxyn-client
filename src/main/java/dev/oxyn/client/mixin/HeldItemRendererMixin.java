package dev.oxyn.client.mixin;

import dev.oxyn.client.module.ModuleManager;
import dev.oxyn.client.render.ButterflyKnife;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zamiast miecza w ręku (pierwsza osoba) rysujemy animowany nóż motylkowy. */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void oxyn$butterfly(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
                                MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!ModuleManager.on("butterfly")) return;
        if (mode != ModelTransformationMode.FIRST_PERSON_RIGHT_HAND && mode != ModelTransformationMode.FIRST_PERSON_LEFT_HAND) return;
        if (!(stack.getItem() instanceof SwordItem)) return;
        ButterflyKnife.render(matrices, leftHanded);
        ci.cancel();
    }
}
