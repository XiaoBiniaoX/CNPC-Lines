package top.cnpclines;

import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import top.cnpclines.command.BinnpclinesCommand;
import top.cnpclines.command.UnquotedStringArgument;
import top.cnpclines.network.NetworkHandler;

@Mod(CNPCLines.MOD_ID)
public class CNPCLines {
    public static final String MOD_ID = "cnpclines";

    public CNPCLines(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::onRegisterArgumentType);
        modEventBus.addListener(NetworkHandler::registerMessages);
    }

    private void onRegisterArgumentType(RegisterEvent event) {
        if (!event.getRegistryKey().equals(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.key())) {
            return;
        }
        ArgumentTypeInfos.registerByClass(UnquotedStringArgument.class, UnquotedStringArgument.INFO);
        event.register(
            BuiltInRegistries.COMMAND_ARGUMENT_TYPE.key(),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "unquoted_string"),
            () -> UnquotedStringArgument.INFO
        );
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BinnpclinesCommand.register(event.getDispatcher());
    }
}
