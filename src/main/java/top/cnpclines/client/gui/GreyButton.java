package top.cnpclines.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class GreyButton extends Button {

    public GreyButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int face = this.isHoveredOrFocused() ? 0xFFB5B5B5 : 0xFF9E9E9E;
        graphics.fill(x, y, x + getWidth(), y + getHeight(), face);
        graphics.fill(x, y, x + getWidth(), y + 1, 0xFFD2D2D2);
        graphics.fill(x, y, x + 1, y + getHeight(), 0xFFD2D2D2);
        graphics.fill(x, y + getHeight() - 1, x + getWidth(), y + getHeight(), 0xFF666666);
        graphics.fill(x + getWidth() - 1, y, x + getWidth(), y + getHeight(), 0xFF666666);
        graphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
            x + getWidth() / 2, y + (getHeight() - 8) / 2, 0xFF484848);
    }
}
