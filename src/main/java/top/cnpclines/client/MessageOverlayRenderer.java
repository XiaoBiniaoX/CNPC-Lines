package top.cnpclines.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.cnpclines.CNPCLines;

@Mod.EventBusSubscriber(modid = CNPCLines.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MessageOverlayRenderer {
    private MessageOverlayRenderer() {
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        HudOverlayManager.renderOverlays(event.getGuiGraphics());
    }
}
