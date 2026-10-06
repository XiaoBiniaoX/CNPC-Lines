package top.cnpclines.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;

public class BetterMaterialButton extends GuiButtonNop {

    public BetterMaterialButton(IGuiInterface gui, int id, int x, int y, int width, int height) {
        super(gui, id, x, y, width, height, "");
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        int color = this.active ? (this.isHovered() ? 0xFFFFA0 : 0xFFFFFF) : 0xA0A0A0;
        int centerX = this.getX() + this.getWidth() / 2;
        graphics.drawCenteredString(Minecraft.getInstance().font, "更好的材质", centerX, this.getY() + 11, color);
        graphics.drawCenteredString(Minecraft.getInstance().font, "编辑", centerX, this.getY() + 23, color);
    }
}
