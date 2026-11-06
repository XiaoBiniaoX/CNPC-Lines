package top.cnpclines.mixin.client;

import net.minecraft.client.Minecraft;
import noppes.npcs.client.gui.global.GuiNPCManageFactions;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpclines.faction.client.FactionEditorButton;
import top.cnpclines.faction.client.FactionScreen;

@Mixin(value = GuiNPCManageFactions.class, remap = false)
public class GuiManageFactionsMixin {
    @Shadow private Faction faction;

    @Inject(method = "init()V", at = @At("TAIL"))
    private void cnpclines$buttons(CallbackInfo ci) {
        if (faction == null || faction.id < 0) return;
        GuiNPCManageFactions self = (GuiNPCManageFactions)(Object)this;
        self.addButton(new FactionEditorButton(self, 18, self.guiLeft + 368, self.guiTop + 60, 45, 38, faction, false));
        self.addButton(new FactionEditorButton(self, 19, self.guiLeft + 368, self.guiTop + 124, 45, 88, faction, true));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpclines$open(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != 18) return;
        GuiNPCManageFactions self = (GuiNPCManageFactions)(Object)this;
        if (self.getTextField(0) != null) self.unFocused(self.getTextField(0));
        self.save();
        Minecraft.getInstance().setScreen(new FactionScreen(self, faction));
        ci.cancel();
    }
}
