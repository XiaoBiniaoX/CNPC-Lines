package top.cnpclines.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue REPLACE_NPC_LINES;
    public static volatile boolean replaceNpcLines = true;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        REPLACE_NPC_LINES = builder
            .comment("Replace NPC speech with the CNPC Lines overlay; false restores CNPC chat and bubbles.",
                "替换 NPC 台词显示；false 恢复 CNPC 原有聊天栏和气泡。",
                "Save this file during gameplay to apply immediately, without restarting.",
                "游戏运行中保存此文件即可热生效，无需重启；不影响 /binnpclines lines1。")
            .translation("cnpclines.config.replaceNpcLines")
            .define("replaceNpcLines", true);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static void setReplaceNpcLines(boolean enabled) {
        REPLACE_NPC_LINES.set(enabled);
        replaceNpcLines = enabled;
        SPEC.save();
    }

    public static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            replaceNpcLines = REPLACE_NPC_LINES.get();
        }
    }
}
