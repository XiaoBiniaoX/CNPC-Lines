package top.cnpclines.config;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue REPLACE_NPC_LINES;
    public static volatile boolean replaceNpcLines = true;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        REPLACE_NPC_LINES = builder
            .comment("Replace NPC speech with the CNPC Lines overlay; false restores CNPC chat and bubbles.",
                "Save this file during gameplay to apply without restarting. Manual command overlays are unaffected.")
            .translation("cnpclines.config.replaceNpcLines")
            .define("replaceNpcLines", true);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            // Reloading may run on the file watcher; Minecraft state is handled by the client tick.
            replaceNpcLines = REPLACE_NPC_LINES.getAsBoolean();
        }
    }
}
