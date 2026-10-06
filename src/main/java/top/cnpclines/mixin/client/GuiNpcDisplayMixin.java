package top.cnpclines.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpclines.client.gui.BetterMaterialButton;
import top.cnpclines.client.gui.GuiBetterMaterialEditor;

@Mixin(value = GuiNpcDisplay.class, remap = false)
public class GuiNpcDisplayMixin {

    @Inject(method = "func_73866_w_()V", at = @At("TAIL"))
    private void cnpclines$addBetterMaterialButton(CallbackInfo ci) {
        GuiNpcDisplay self = (GuiNpcDisplay) (Object) this;
        self.addButton(new BetterMaterialButton(17, self.guiLeft + 365, self.guiTop + 73, 53, 43));
    }

    @Inject(method = "func_146284_a(Lnet/minecraft/client/gui/GuiButton;)V", at = @At("HEAD"), cancellable = true)
    private void cnpclines$openBetterMaterialEditor(GuiButton button, CallbackInfo ci) {
        if (button.id != 17) {
            return;
        }
        GuiNpcDisplay self = (GuiNpcDisplay) (Object) this;
        Minecraft.getMinecraft().displayGuiScreen(new GuiBetterMaterialEditor(self));
        ci.cancel();
    }
}
