package top.cnpclines.client;

import java.util.Collections;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import top.cnpclines.CNPCLines;

public final class CnpclinesGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraft) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parent) {
        ClientConfig.reloadIfChanged();
        return new GuiConfig(parent,
            new ConfigElement(ClientConfig.configuration().getCategory(Configuration.CATEGORY_CLIENT)).getChildElements(),
            CNPCLines.MOD_ID, false, false, I18n.format("cnpclines.config.title"));
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }
}
