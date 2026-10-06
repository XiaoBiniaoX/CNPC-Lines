package top.cnpclines.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

final class TabIcons {
    private static final Map<ResourceLocation, ResourceLocation> BLACK = new HashMap<>();

    private TabIcons() {
    }

    static ResourceLocation black(ResourceLocation icon) {
        return BLACK.computeIfAbsent(icon, TabIcons::create);
    }

    private static ResourceLocation create(ResourceLocation icon) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Optional<Resource> resource = minecraft.getResourceManager().getResource(icon);
            if (resource.isEmpty()) {
                return icon;
            }
            byte[] bytes;
            try (InputStream stream = resource.get().open()) {
                bytes = stream.readAllBytes();
            }
            NativeImage image = NativeImage.read(bytes);
            int width = image.getWidth();
            int height = image.getHeight();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setPixelRGBA(x, y, image.getPixelRGBA(x, y) & 0xFF000000);
                }
            }
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                icon.getNamespace(), "gui_tinted/" + icon.getPath());
            minecraft.getTextureManager().register(id, new DynamicTexture(image));
            return id;
        } catch (Exception exception) {
            return icon;
        }
    }
}
