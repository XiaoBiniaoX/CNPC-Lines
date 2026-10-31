package top.cnpclines.client;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.slf4j.Logger;
import top.cnpclines.CNPCLines;

@EventBusSubscriber(modid = CNPCLines.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class CommandTreeLocalizer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, String> ARG_KEYS = new HashMap<>();

    static {
        ARG_KEYS.put("player", "cnpclines.arg.player");
        ARG_KEYS.put("message", "cnpclines.arg.message");
        ARG_KEYS.put("name", "cnpclines.arg.name");
        ARG_KEYS.put("color", "cnpclines.arg.color");
        ARG_KEYS.put("maxSymbolsFirstLine", "cnpclines.arg.maxSymbolsFirstLine");
        ARG_KEYS.put("maxSymbolsOtherLines", "cnpclines.arg.maxSymbolsOtherLines");
        ARG_KEYS.put("icon", "cnpclines.arg.icon");
    }

    private static Field nameField;
    private static boolean renameFailed;
    private static CommandNode<?> processedTop;
    private static String processedLang;

    private CommandTreeLocalizer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        localize();
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
        int renamed = renameSubtree(top);
        LOGGER.info(
            "[cnpclines] command tree localize: lang={}, renamedNodes={}", lang, renamed
        );
        processedTop = top;
        processedLang = lang;
    }

    private static int renameSubtree(CommandNode<?> node) {
        int renamed = 0;
        if (node instanceof ArgumentCommandNode<?, ?> argumentNode) {
            String key = ARG_KEYS.get(argumentNode.getName());
            if (key != null) {
                String target = I18n.get(key);
                if (!target.equals(key) && !target.equals(argumentNode.getName())
                    && rename(argumentNode, target)) {
                    renamed++;
                }
            }
        }
        for (CommandNode<?> child : node.getChildren()) {
            renamed += renameSubtree(child);
        }
        return renamed;
    }

    private static boolean rename(ArgumentCommandNode<?, ?> node, String newName) {
        try {
            if (nameField == null) {
                nameField = ArgumentCommandNode.class.getDeclaredField("name");
                nameField.setAccessible(true);
            }
            String oldName = node.getName();
            nameField.set(node, newName);
            if (!newName.equals(node.getName())) {
                renameFailed = true;
                LOGGER.error("[cnpclines] rename to {} not effective, current={}", newName, node.getName());
                return false;
            }
            LOGGER.info("[cnpclines] renamed command argument {} -> {}", oldName, newName);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            renameFailed = true;
            LOGGER.error("[cnpclines] failed to rename command argument node", e);
            return false;
        }
    }
}
