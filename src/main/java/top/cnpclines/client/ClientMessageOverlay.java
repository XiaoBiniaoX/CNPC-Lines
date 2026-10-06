package top.cnpclines.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.cnpclines.network.OpenCustomBarOverlayPacket;

public final class ClientMessageOverlay {
    private static final Logger LOGGER = LogManager.getLogger("cnpclines");
    private static final String ICONS = "cnpclines:textures/overlay/message/icons/";

    private ClientMessageOverlay() {
    }

    public static void show(OpenCustomBarOverlayPacket packet) {
        show(packet, 10000L);
    }

    public static void show(OpenCustomBarOverlayPacket packet, long displayMillis) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        LOGGER.info("[cnpclines] overlay show: displayMillis={}, icon={}", displayMillis, packet.getIcon());

        for (int i = 0; i < 500; i++) {
            HudOverlayManager.removeComponent(i);
        }

        String name = packet.getName();
        String coloredName = "[" + name + "]§f:";
        int prefixLen = coloredName.length() + 1;
        int firstCap = Math.max(1, packet.getMaxSymbolsFirstLine() - prefixLen);
        String[] msgLines = splitMessage(
            packet.getMessage(), firstCap, packet.getMaxSymbolsOtherLines(), 7
        );

        String[] lines = new String[msgLines.length];
        if (msgLines.length > 0) {
            lines[0] = coloredName + " " + msgLines[0];
        }
        for (int i = 1; i < msgLines.length; i++) {
            lines[i] = msgLines[i];
        }

        int maxMessageWidth = 0;
        for (String line : lines) {
            maxMessageWidth = Math.max(maxMessageWidth, font.getStringWidth(line));
        }

        boolean iconVisible = !packet.getIcon().isEmpty();
        int padding = 5;
        int iconWidth = 16;
        int backgroundWidth = maxMessageWidth + 2 * padding + (iconVisible ? iconWidth + padding : 0);

        ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        int bgX = (screenWidth - backgroundWidth) / 2;
        bgX = (int) (bgX - 11.5);
        int bgY = screenHeight - 90;
        int textX = bgX + padding + (iconVisible ? iconWidth + padding : 0);
        int textY = bgY + 5;
        int iconX = bgX + padding / 2;
        int iconY = bgY + 3;

        if (msgLines.length > 0) {
            HudOverlayManager.addOverlayText(1, coloredName, textX, textY, packet.getColor());
            HudOverlayManager.addOverlayText(2, msgLines[0],
                textX + font.getStringWidth(coloredName + " "), textY, "#FFFFFF");
        }

        for (int i = 1; i < msgLines.length; i++) {
            HudOverlayManager.addOverlayText(i + 2, msgLines[i], textX, textY + 10 * i, "#FFFFFF");
        }

        if (iconVisible) {
            String iconPath = resolveIconPath(packet.getIcon());
            if (iconPath != null) {
                boolean dynamicSkin = iconPath.contains("cnpclines_npc");
                float iconScale = dynamicSkin ? 0.875F : 1.375F;
                int iconDrawX = iconX + (dynamicSkin ? 1 : 0);
                int iconDrawY = dynamicSkin ? bgY + 2 : iconY - 1 - 4;
                HudOverlayManager.addIconOverlay(101, iconPath, iconDrawX, iconDrawY, iconScale);
            }
        }

        int linesCount = lines.length;
        int backgroundHeight = linesCount == 1 ? 19 : 19 + (linesCount - 1) * 10;
        HudOverlayManager.addBackgroundOverlay(
            102, "cnpclines:textures/overlay/message/actionbar_message.png",
            bgX, bgY, backgroundWidth + 20, backgroundHeight
        );

        HudOverlayManager.fadeInAll();
        if (displayMillis > 0L) {
            for (int i = 0; i < 500; i++) {
                HudOverlayManager.fadeOut(i, (int) (displayMillis + 700L));
            }
        }
    }

    private static String resolveIconPath(String icon) {
        String value = icon;
        if (value.startsWith("cnpcadditions:")) {
            value = "cnpclines:" + value.substring("cnpcadditions:".length());
        }
        if (value.contains(":")) {
            return value;
        }
        switch (value) {
            case "non":
                return ICONS + "non.png";
            case "npc":
                return ICONS + "npc.png";
            case "invisible":
            case "off":
                return ICONS + "invisible.png";
            default:
                return ICONS + (value.endsWith(".png") ? value : value + ".png");
        }
    }

    private static String[] splitMessage(String message, int maxSymbolsFirstLine, int maxSymbolsOtherLines, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (message == null || message.isEmpty()) {
            lines.add("");
            return lines.toArray(new String[0]);
        }
        int cap = maxSymbolsFirstLine;
        int start = 0;
        int len = message.length();
        while (start < len) {
            if (lines.size() >= maxLines - 1) {
                lines.add(message.substring(start).trim());
                break;
            }
            int end = Math.min(start + cap, len);
            if (end < len) {
                int brk = -1;
                for (int i = end; i > start + Math.max(1, cap / 2); i--) {
                    if (message.charAt(i - 1) == ' ') {
                        brk = i;
                        break;
                    }
                }
                end = brk > 0 ? brk : end;
            }
            lines.add(message.substring(start, end).trim());
            start = end;
            while (start < len && message.charAt(start) == ' ') {
                start++;
            }
            cap = maxSymbolsOtherLines;
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines.toArray(new String[0]);
    }
}
