package top.cnpclines.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import top.cnpclines.CNPCLines;

@EventBusSubscriber(modid = CNPCLines.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class MessageOverlayRenderer {
    private MessageOverlayRenderer() {
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        HudOverlayManager.renderOverlays(event.getGuiGraphics());
    }
}
