package top.cnpclines;

import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;
import top.cnpclines.command.BinnpclinesCommand;
import top.cnpclines.command.UnquotedStringArgument;
import top.cnpclines.network.NetworkHandler;

@Mod(CNPCLines.MOD_ID)
public class CNPCLines {
    public static final String MOD_ID = "cnpclines";

    public CNPCLines() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onRegisterArgumentType);
    }

    private void onRegisterArgumentType(RegisterEvent event) {
        if (!event.getRegistryKey().equals(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.key())) {
            return;
        }
        ArgumentTypeInfos.registerByClass(UnquotedStringArgument.class, UnquotedStringArgument.INFO);
        event.register(
            BuiltInRegistries.COMMAND_ARGUMENT_TYPE.key(),
            new ResourceLocation(MOD_ID, "unquoted_string"),
            () -> UnquotedStringArgument.INFO
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        NetworkHandler.registerMessages();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BinnpclinesCommand.register(event.getDispatcher());
    }
}
