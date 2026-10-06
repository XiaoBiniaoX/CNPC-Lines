package top.cnpclines.client;

import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import top.cnpclines.CNPCLines;

@Mod.EventBusSubscriber(modid = CNPCLines.MOD_ID, value = Side.CLIENT)
public final class MessageOverlayRenderer {
    private MessageOverlayRenderer() {
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRenderGui(RenderGameOverlayEvent.Pre event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        HudOverlayManager.renderOverlays();
    }
}
