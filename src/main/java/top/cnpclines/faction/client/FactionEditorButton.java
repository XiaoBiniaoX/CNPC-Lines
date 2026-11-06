package top.cnpclines.faction.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import top.cnpclines.faction.FactionVisual;

public final class FactionEditorButton extends GuiButtonNop {
    private final Faction faction;
    private final boolean preview;
    public FactionEditorButton(IGuiInterface parent, int id, int x, int y, int w, int h, Faction faction, boolean preview) {
        super(parent, id, x, y, w, h, "");
        this.faction = faction;
        this.preview = preview;
        if (preview) this.active = false;
    }
    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        if (!preview) super.renderWidget(g, mx, my, partial);
        int x = getX(), y = getY();
        var font = Minecraft.getInstance().font;
        if (preview) {
            g.drawCenteredString(font, I18n.get("cnpclines.faction.flag"), x + getWidth() / 2, y + 5, 0xFFFFFF);
            FactionScreen.drawFlag(g, ((FactionVisual)faction).cnpclines$visual().getString("Flag"),
                x + 5, y + 19, getWidth() - 10, getHeight() - 25);
        } else {
            String label = I18n.get("cnpclines.faction.visual_editor");
            var lines = font.split(net.minecraft.network.chat.Component.literal(label), getWidth() - 4);
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x + (getWidth() - font.width(lines.get(i))) / 2, y + 5 + i * 10, 0xFFFFFF, false);
        }
    }
}
