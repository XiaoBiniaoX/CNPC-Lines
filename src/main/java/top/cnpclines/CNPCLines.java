package top.cnpclines;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import top.cnpclines.client.CustomSkinsPack;
import top.cnpclines.client.ClientConfig;
import top.cnpclines.client.NpcLineQueue;
import top.cnpclines.command.BinnpclinesCommand;
import top.cnpclines.network.NetworkHandler;

@Mod(modid = CNPCLines.MOD_ID, name = "CNPC Lines", version = "1.1.0",
    guiFactory = "top.cnpclines.client.CnpclinesGuiFactory")
public class CNPCLines {
    public static final String MOD_ID = "cnpclines";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (event.getSide().isClient()) {
            ClientConfig.init(event.getModConfigurationDirectory());
        }
        NetworkHandler.registerMessages();
        FMLCommonHandler.instance().bus().register(NpcLineQueue.class);
        FMLCommonHandler.instance().bus().register(CustomSkinsPack.class);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new BinnpclinesCommand());
    }
}
