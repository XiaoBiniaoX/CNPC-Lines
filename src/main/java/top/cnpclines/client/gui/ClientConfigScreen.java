package top.cnpclines.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.cnpclines.CNPCLines;
import top.cnpclines.config.ClientConfig;

@Mod.EventBusSubscriber(modid = CNPCLines.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientConfigScreen extends Screen {
    private final Screen parent;

    public ClientConfigScreen(Screen parent) {
        super(Component.translatable("cnpclines.config.title"));
        this.parent = parent;
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory(ClientConfigScreen::new));
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(360, this.width - 40);
        this.addRenderableWidget(CycleButton.onOffBuilder(ClientConfig.replaceNpcLines)
            .withTooltip(value -> Tooltip.create(Component.translatable("cnpclines.config.replaceNpcLines.tooltip")))
            .create((this.width - buttonWidth) / 2, this.height / 2 - 10, buttonWidth, 20,
                Component.translatable("cnpclines.config.replaceNpcLines"),
                (button, enabled) -> ClientConfig.setReplaceNpcLines(enabled)));
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
            .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
