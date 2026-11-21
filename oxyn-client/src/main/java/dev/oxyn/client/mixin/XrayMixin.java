package dev.oxyn.client.mixin;

import dev.oxyn.client.feature.Cheats;
import dev.oxyn.client.module.ModuleManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Moduł Xray: rysuje tylko ściany wybranych bloków (rudy itp.). */
@Mixin(Block.class)
public class XrayMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void oxyn$xray(BlockState state, BlockView world, BlockPos pos, Direction side,
                                  BlockPos otherPos, CallbackInfoReturnable<Boolean> cir) {
        if (!ModuleManager.on("xray")) return;
        cir.setReturnValue(Cheats.isXrayBlock(state));
    }
}
