package top.cnpclines.client;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
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
    private static final Map<String, String> EN_TO_ZH = new HashMap<>();
    private static final Map<String, String> ZH_TO_EN = new HashMap<>();

    static {
        EN_TO_ZH.put("player", "玩家");
        EN_TO_ZH.put("message", "消息");
        EN_TO_ZH.put("name", "名字");
        EN_TO_ZH.put("color", "颜色");
        EN_TO_ZH.put("maxSymbolsFirstLine", "首行最大字符数");
        EN_TO_ZH.put("maxSymbolsOtherLines", "其余行最大字符数");
        EN_TO_ZH.put("icon", "图标");
        for (Map.Entry<String, String> entry : EN_TO_ZH.entrySet()) {
            ZH_TO_EN.put(entry.getValue(), entry.getKey());
        }
    }

    private static Field nameField;
    private static boolean renameFailed;
    private static CommandNode<?> processedTop;
    private static boolean processedChinese;

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
        String gameLanguage = minecraft.getLanguageManager().getSelected();
        String systemLanguage = Locale.getDefault().getLanguage();
        boolean chinese = gameLanguage.startsWith("zh") || "zh".equals(systemLanguage);
        if (top == processedTop && chinese == processedChinese) {
            return;
        }
        int renamed = renameSubtree(top, chinese);
        LOGGER.info(
            "[cnpclines] command tree localize: gameLang={}, systemLang={}, chinese={}, renamedNodes={}, effective={}",
            gameLanguage, systemLanguage, chinese, renamed, renamed > 0 || !chinese
        );
        processedTop = top;
        processedChinese = chinese;
    }

    private static int renameSubtree(CommandNode<?> node, boolean chinese) {
        int renamed = 0;
        if (node instanceof ArgumentCommandNode<?, ?> argumentNode) {
            String current = argumentNode.getName();
            String target = chinese ? EN_TO_ZH.get(current) : ZH_TO_EN.get(current);
            if (target != null && rename(argumentNode, target)) {
                renamed++;
            }
        }
        for (CommandNode<?> child : node.getChildren()) {
            renamed += renameSubtree(child, chinese);
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
