package top.cnpclines.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.Faction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpclines.faction.FactionVisual;

@Mixin(value = Faction.class, remap = false)
public class FactionVisualMixin implements FactionVisual {
    @Unique private CompoundTag cnpclines$visual = new CompoundTag();
    @Override public CompoundTag cnpclines$visual() { return cnpclines$visual; }
    @Override public void cnpclines$visual(CompoundTag value) { cnpclines$visual = value.copy(); }

    @Inject(method = "readNBT", at = @At("TAIL"))
    private void cnpclines$read(CompoundTag tag, CallbackInfo ci) {
        cnpclines$visual = tag.getCompound(FactionVisual.KEY).copy();
    }

    @Inject(method = "writeNBT", at = @At("RETURN"))
    private void cnpclines$write(CompoundTag tag, CallbackInfoReturnable<CompoundTag> ci) {
        ci.getReturnValue().put(FactionVisual.KEY, cnpclines$visual.copy());
    }
}
