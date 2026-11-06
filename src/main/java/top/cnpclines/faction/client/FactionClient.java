package top.cnpclines.faction.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import noppes.npcs.client.gui.player.GuiFaction;

@Mod.EventBusSubscriber(modid = "cnpclines", value = Side.CLIENT)
public final class FactionClient {
    @SubscribeEvent public static void opening(GuiOpenEvent event) {
        if (event.getGui() instanceof GuiFaction) event.setGui(new FactionScreen(null, null));
    }
    public static void receive(NBTTagCompound data) {
        Minecraft mc = Minecraft.getMinecraft();
        if (data != null && mc.player != null && mc.currentScreen instanceof FactionScreen) ((FactionScreen)mc.currentScreen).receive(data);
    }
}
