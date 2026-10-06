package top.cnpclines.mixin.client;

import noppes.npcs.shared.client.util.NoppesStringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpclines.client.gui.ResourceIndex;

@Mixin(value = NoppesStringUtils.class, remap = false)
public class NoppesStringUtilsMixin {

    @Inject(method = "cleanResource", at = @At("HEAD"), cancellable = true)
    private static void cnpclines$resolveAlias(String value, CallbackInfoReturnable<String> cir) {
        String alias = ResourceIndex.resolveAlias(value);
        if (alias != null) {
            cir.setReturnValue(alias);
        }
    }
}
