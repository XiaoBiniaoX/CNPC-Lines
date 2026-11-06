package top.cnpclines.faction.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.client.gui.player.GuiFaction;

@Mod.EventBusSubscriber(modid = "cnpclines", value = Dist.CLIENT)
public final class FactionClient {
    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof GuiFaction) event.setNewScreen(new FactionScreen(null, null));
    }

    public static void receive(CompoundTag data) {
        if (data == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof FactionScreen screen) screen.receive(data);
    }
}
