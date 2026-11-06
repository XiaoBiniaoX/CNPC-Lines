package top.cnpclines;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import top.cnpclines.command.BinnpclinesCommand;
import top.cnpclines.network.NetworkHandler;

@Mod(modid = CNPCLines.MOD_ID, name = "CNPC Lines", version = "1.2.0",
    dependencies = "required-after:customnpcs;required-after:cnpcplus@[4.0.6,)",
    guiFactory = "top.cnpclines.client.CnpclinesGuiFactory")
public class CNPCLines {
    public static final String MOD_ID = "cnpclines";
    @SidedProxy(clientSide = "top.cnpclines.client.ClientProxy", serverSide = "top.cnpclines.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        NetworkHandler.registerMessages();
        proxy.preInit(event.getModConfigurationDirectory());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new BinnpclinesCommand());
    }
}
