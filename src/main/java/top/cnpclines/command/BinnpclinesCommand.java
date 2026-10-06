package top.cnpclines.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import top.cnpclines.network.NetworkHandler;
import top.cnpclines.network.OpenCustomBarOverlayPacket;

public final class BinnpclinesCommand {
    private BinnpclinesCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("binnpclines")
                .then(
                    Commands.literal("lines1")
                        .then(
                            Commands.argument("player", EntityArgument.players())
                                .then(
                                    Commands.argument("message", UnquotedStringArgument.unquotedString())
                                        .then(
                                            Commands.argument("name", UnquotedStringArgument.unquotedString())
                                                .then(
                                                    Commands.argument("color", UnquotedStringArgument.unquotedString())
                                                        .then(
                                                            Commands.argument("maxSymbolsFirstLine", FloatArgumentType.floatArg())
                                                                .then(
                                                                    Commands.argument("maxSymbolsOtherLines", FloatArgumentType.floatArg())
                                                                        .executes(context -> executeCommand(context, ""))
                                                                        .then(
                                                                            Commands.argument("icon", StringArgumentType.greedyString())
                                                                                .suggests(IconSuggestions.PROVIDER)
                                                                                .executes(context ->
                                                                                    executeCommand(context, StringArgumentType.getString(context, "icon"))
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int executeCommand(CommandContext<CommandSourceStack> context, String icon) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "player");
        String message = StringArgumentType.getString(context, "message");
        String name = StringArgumentType.getString(context, "name");
        String color = StringArgumentType.getString(context, "color");
        float maxSymbolsFirstLine = FloatArgumentType.getFloat(context, "maxSymbolsFirstLine");
        float maxSymbolsOtherLines = FloatArgumentType.getFloat(context, "maxSymbolsOtherLines");
        String iconPath = normalizeIcon(icon);

        for (ServerPlayer player : players) {
            NetworkHandler.sendToPlayer(player, new OpenCustomBarOverlayPacket(
                message, name, color, iconPath, (int) maxSymbolsFirstLine, (int) maxSymbolsOtherLines
            ));
        }

        return 1;
    }

    private static String normalizeIcon(String icon) {
        String value = icon == null ? "" : icon.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }
}
