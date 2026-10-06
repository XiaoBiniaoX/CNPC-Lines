package top.cnpclines.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import top.cnpclines.network.NetworkHandler;
import top.cnpclines.network.OpenCustomBarOverlayPacket;

public final class BinnpclinesCommand extends CommandBase {
    private static final String USAGE =
        "binnpclines lines1 <player> <message> <name> <color> <maxSymbolsFirstLine> <maxSymbolsOtherLines> [icon]";

    @Override
    public String getName() {
        return "binnpclines";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 7 || !"lines1".equalsIgnoreCase(args[0])) {
            throw new WrongUsageException(getUsage(sender));
        }
        List<EntityPlayerMP> players = getPlayers(server, sender, args[1]);
        String message = args[2];
        String name = args[3];
        String color = args[4];
        int maxSymbolsFirstLine = (int) parseFloatArg(args[5]);
        int maxSymbolsOtherLines = (int) parseFloatArg(args[6]);
        StringBuilder iconBuilder = new StringBuilder();
        for (int i = 7; i < args.length; i++) {
            if (iconBuilder.length() > 0) {
                iconBuilder.append(' ');
            }
            iconBuilder.append(args[i]);
        }
        String iconPath = normalizeIcon(iconBuilder.toString());

        for (EntityPlayerMP player : players) {
            NetworkHandler.sendToPlayer(player, new OpenCustomBarOverlayPacket(
                message, name, color, iconPath, maxSymbolsFirstLine, maxSymbolsOtherLines
            ));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "lines1");
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, server.getPlayerList().getOnlinePlayerNames());
        }
        if (args.length >= 8) {
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String icon : IconSuggestions.icons()) {
                if (icon.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(icon);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private static float parseFloatArg(String value) throws CommandException {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new CommandException("commands.generic.num.invalid", value);
        }
    }

    private static String normalizeIcon(String icon) {
        String value = icon == null ? "" : icon.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }
}
