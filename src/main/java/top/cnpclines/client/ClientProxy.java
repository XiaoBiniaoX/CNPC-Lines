package top.cnpclines.client;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import top.cnpclines.CommonProxy;
import top.cnpclines.faction.client.FactionClient;
import top.cnpclines.network.NetworkHandler;

public class ClientProxy extends CommonProxy {
    @Override public void preInit(File configDirectory) {
        ClientConfig.init(configDirectory);
        FMLCommonHandler.instance().bus().register(NpcLineQueue.class);
        FMLCommonHandler.instance().bus().register(CustomSkinsPack.class);
        NetworkHandler.overlay = packet -> Minecraft.getMinecraft().addScheduledTask(() -> ClientMessageOverlay.show(packet));
        NetworkHandler.factionSnapshot = data -> Minecraft.getMinecraft().addScheduledTask(() -> FactionClient.receive(data));
    }
}
