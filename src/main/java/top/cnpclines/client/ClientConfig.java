package top.cnpclines.client;

import java.io.File;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.cnpclines.CNPCLines;

@Mod.EventBusSubscriber(modid = CNPCLines.MOD_ID, value = Side.CLIENT)
public final class ClientConfig {
    private static final Logger LOGGER = LogManager.getLogger(CNPCLines.MOD_ID);
    private static Configuration config;
    private static boolean customNpcLines = true;
    private static long modified;
    private static long nextCheck;

    private ClientConfig() {
    }

    public static void init(File directory) {
        config = new Configuration(new File(directory, "cnpclines.cfg"));
        sync();
    }

    public static boolean customNpcLinesEnabled() {
        return customNpcLines;
    }

    public static Configuration configuration() {
        return config;
    }

    private static void sync() {
        // The boolean overload silently replaces invalid input with the default before validation.
        Property property = config.get(Configuration.CATEGORY_CLIENT, "customNpcLines", "true",
            "Replace NPC speech with the custom dialogue HUD. False restores CNPC speech bubbles and chat. "
                + "Client-only; save this file while playing to reload within one second.", Property.Type.BOOLEAN);
        property.setLanguageKey("cnpclines.config.custom_npc_lines")
            .setRequiresMcRestart(false).setRequiresWorldRestart(false);
        config.setCategoryLanguageKey(Configuration.CATEGORY_CLIENT, "cnpclines.config.client");
        if (!property.isBooleanValue()) {
            property.setValue(customNpcLines);
            modified = config.getConfigFile().lastModified();
            LOGGER.warn("[cnpclines] customNpcLines must be true or false; keeping the previous setting");
            return;
        }
        boolean enabled = property.getBoolean();
        if (config.hasChanged()) {
            config.save();
        }
        modified = config.getConfigFile().lastModified();
        customNpcLines = enabled;
        if (!enabled) {
            NpcLineQueue.clear();
        }
    }

    public static void reloadIfChanged() {
        if (config == null || System.currentTimeMillis() < nextCheck) {
            return;
        }
        nextCheck = System.currentTimeMillis() + 1000L;
        File file = config.getConfigFile();
        if (!file.isFile() || file.lastModified() == modified) {
            return;
        }
        try {
            config.load();
            if (!config.hasKey(Configuration.CATEGORY_CLIENT, "customNpcLines")) {
                throw new IllegalArgumentException("customNpcLines is missing");
            }
            sync();
        } catch (RuntimeException e) {
            config.get(Configuration.CATEGORY_CLIENT, "customNpcLines", customNpcLines).setValue(customNpcLines);
            modified = file.lastModified();
            LOGGER.warn("[cnpclines] Cannot reload client config; keeping the previous setting", e);
        }
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (CNPCLines.MOD_ID.equals(event.getModID()) && config != null) {
            sync();
        }
    }
}
