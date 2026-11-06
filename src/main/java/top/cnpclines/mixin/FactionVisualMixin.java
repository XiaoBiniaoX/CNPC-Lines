package top.cnpclines.mixin;

import net.minecraft.nbt.NBTTagCompound;
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
    @Unique private NBTTagCompound cnpclines$visual = new NBTTagCompound();
    @Override public NBTTagCompound cnpclines$visual() { return cnpclines$visual; }
    @Override public void cnpclines$visual(NBTTagCompound value) { cnpclines$visual = value.copy(); }

    @Inject(method = "readNBT", at = @At("TAIL"))
    private void cnpclines$read(NBTTagCompound tag, CallbackInfo ci) {
        cnpclines$visual = tag.getCompoundTag(FactionVisual.KEY).copy();
    }

    @Inject(method = "writeNBT", at = @At("RETURN"))
    private void cnpclines$write(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> ci) {
        ci.getReturnValue().setTag(FactionVisual.KEY, cnpclines$visual.copy());
    }
}
