package top.cnpclines.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import noppes.npcs.client.gui.util.GuiNpcButton;

public class BetterMaterialButton extends GuiNpcButton {

    public BetterMaterialButton(int id, int x, int y, int width, int height) {
        super(id, x, y, width, height, "");
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseX < this.x + this.width
            && mouseY >= this.y && mouseY < this.y + this.height;
        int state = this.getHoverState(this.hovered);
        mc.getTextureManager().bindTexture(BUTTON_TEXTURES);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        Gui.drawScaledCustomSizeModalRect(this.x, this.y, 0.0F, 46.0F + state * 20.0F,
            200, 20, this.width, this.height, 256.0F, 256.0F);
        int color = this.enabled ? (this.hovered ? 0xFFFFA0 : 0xFFFFFF) : 0xA0A0A0;
        int centerX = this.x + this.width / 2;
        FontRenderer font = mc.fontRenderer;
        String line1 = "更好的材质";
        String line2 = "编辑";
        font.drawString(line1, centerX - font.getStringWidth(line1) / 2, this.y + 11, color);
        font.drawString(line2, centerX - font.getStringWidth(line2) / 2, this.y + 23, color);
    }
}
