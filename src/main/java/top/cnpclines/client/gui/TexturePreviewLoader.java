package top.cnpclines.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

public final class TexturePreviewLoader {
    private static final Map<String, Preview> REGISTERED = new HashMap<>();
    private static final Set<String> INFLIGHT = new HashSet<>();
    private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<>();
    private static int counter = 0;

    public record Preview(ResourceLocation front, ResourceLocation back, int width, int height, boolean failed) {
    }

    private record Pending(String key, int[] pixels, int width, int height) {
    }

    private TexturePreviewLoader() {
    }

    public static Preview peek(ResourceLocation location) {
        String key = location.toString();
        Preview cached = REGISTERED.get(key);
        if (cached != null) {
            return cached;
        }
        if (INFLIGHT.contains(key)) {
            return null;
        }
        byte[] png = readBytes(location);
        if (png == null) {
            Preview failed = new Preview(null, null, 0, 0, true);
            REGISTERED.put(key, failed);
            return failed;
        }
        INFLIGHT.add(key);
        CompletableFuture.runAsync(() -> {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
                if (image == null) {
                    PENDING.add(new Pending(key, null, 0, 0));
                    return;
                }
                int width = image.getWidth();
                int height = image.getHeight();
                int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
                PENDING.add(new Pending(key, pixels, width, height));
            } catch (Exception e) {
                PENDING.add(new Pending(key, null, 0, 0));
            }
        });
        return null;
    }

    public static void drain() {
        if (PENDING.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Pending pending;
        while ((pending = PENDING.poll()) != null) {
            INFLIGHT.remove(pending.key());
            if (pending.pixels() == null) {
                REGISTERED.put(pending.key(), new Preview(null, null, 0, 0, true));
                continue;
            }
            try {
                int width = pending.width();
                int height = pending.height();
                NativeImage frontImage = new NativeImage(width, height, false);
                NativeImage backImage = new NativeImage(width, height, false);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = pending.pixels()[y * width + x];
                        int abgr = (argb << 24) | ((argb & 0xFF) << 16) | (argb & 0x0000FF00) | ((argb >>> 16) & 0xFF);
                        frontImage.setPixelRGBA(x, y, abgr);
                        backImage.setPixelRGBA(width - 1 - x, y, abgr);
                    }
                }
                ResourceLocation front = mc.getTextureManager()
                    .register("cnpclines_pvfront_" + counter++, new DynamicTexture(frontImage));
                ResourceLocation back = mc.getTextureManager()
                    .register("cnpclines_pvback_" + counter++, new DynamicTexture(backImage));
                REGISTERED.put(pending.key(), new Preview(front, back, width, height, false));
            } catch (Exception e) {
                REGISTERED.put(pending.key(), new Preview(null, null, 0, 0, true));
            }
        }
    }

    private static byte[] readBytes(ResourceLocation location) {
        byte[] weird = ResourceIndex.weirdBytes(location);
        if (weird != null) {
            return weird;
        }
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().open()) {
                    return stream.readAllBytes();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
