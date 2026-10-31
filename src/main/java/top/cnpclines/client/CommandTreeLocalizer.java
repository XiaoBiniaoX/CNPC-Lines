package top.cnpclines.client;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import top.cnpclines.CNPCLines;

@Mod.EventBusSubscriber(modid = CNPCLines.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommandTreeLocalizer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] ARGS = {
        "player", "message", "name", "color",
        "maxSymbolsFirstLine", "maxSymbolsOtherLines", "icon"
    };
    private static final Map<String, String> VALUE_TO_EN = new HashMap<>();

    private static Field nameField;
    private static boolean renameFailed;
    private static CommandNode<?> processedTop;
    private static String processedLang;

    private CommandTreeLocalizer() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            localize();
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        localize();
    }

    public static void localize() {
        if (renameFailed) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        CommandNode<?> root = minecraft.getConnection().getCommands().getRoot();
        CommandNode<?> top = root.getChild("binnpclines");
        if (top == null) {
            processedTop = null;
            return;
        }
        String lang = minecraft.getLanguageManager().getSelected();
        if (top == processedTop && lang.equals(processedLang)) {
            return;
        }
        renameSubtree(top);
        processedTop = top;
        processedLang = lang;
    }

    private static void renameSubtree(CommandNode<?> node) {
        if (node instanceof ArgumentCommandNode<?, ?> argumentNode) {
            String current = argumentNode.getName();
            String en = enName(current);
            if (en != null) {
                String target = I18n.get("cnpclines.arg." + en);
                if (target.isEmpty() || target.equals("cnpclines.arg." + en)) {
                    target = en;
                }
                if (!target.equals(current) && rename(argumentNode, target)) {
                    VALUE_TO_EN.put(target, en);
                }
            }
        }
        for (CommandNode<?> child : node.getChildren()) {
            renameSubtree(child);
        }
    }

    private static String enName(String current) {
        for (String arg : ARGS) {
            if (arg.equals(current)) {
                return arg;
            }
        }
        return VALUE_TO_EN.get(current);
    }

    private static boolean rename(ArgumentCommandNode<?, ?> node, String newName) {
        try {
            if (nameField == null) {
                nameField = ArgumentCommandNode.class.getDeclaredField("name");
                nameField.setAccessible(true);
            }
            nameField.set(node, newName);
            if (!newName.equals(node.getName())) {
                renameFailed = true;
                LOGGER.error("[cnpclines] rename to {} not effective, current={}", newName, node.getName());
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            renameFailed = true;
            LOGGER.error("[cnpclines] failed to rename command argument node", e);
            return false;
        }
    }
}
