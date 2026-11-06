package top.cnpclines.faction.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.controllers.data.Faction;
import top.cnpclines.faction.FactionVisual;

public final class FactionEditorButton extends GuiNpcButton {
    private final Faction faction;
    private final boolean preview;
    public FactionEditorButton(int id, int x, int y, int w, int h, Faction faction, boolean preview) {
        super(id, x, y, w, h, ""); this.faction = faction; this.preview = preview;
        if (preview) this.enabled = false;
    }
    @Override public void drawButton(Minecraft mc, int mx, int my, float partial) {
        if (!visible) return;
        if (preview) {
            drawCenteredString(mc.fontRenderer, I18n.format("cnpclines.faction.flag"), x + width / 2, y + 5, 0xFFFFFF);
            FactionScreen.drawFlag(((FactionVisual)faction).cnpclines$visual().getString("Flag"), x + 5, y + 19, width - 10, height - 25);
        } else {
            // Vanilla samples height pixels from a 20px atlas row, leaking other sprites on tall buttons.
            this.hovered = mx >= x && mx < x + width && my >= y && my < y + height;
            int state = this.getHoverState(this.hovered);
            mc.getTextureManager().bindTexture(BUTTON_TEXTURES);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
            Gui.drawScaledCustomSizeModalRect(x, y, 0.0F, 46.0F + state * 20.0F,
                200, 20, width, height, 256.0F, 256.0F);
            List<String> lines = mc.fontRenderer.listFormattedStringToWidth(I18n.format("cnpclines.faction.visual_editor"), width - 4);
            for (int i = 0; i < lines.size(); i++) drawCenteredString(mc.fontRenderer, lines.get(i), x + width / 2, y + 5 + i * 10, 0xFFFFFF);
        }
    }
}
