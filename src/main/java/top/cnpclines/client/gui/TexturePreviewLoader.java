package top.cnpclines.client.gui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

public final class TexturePreviewLoader {
    private static final Map<String, Preview> REGISTERED = new HashMap<>();
    private static final Set<String> INFLIGHT = new HashSet<>();
    private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<>();
    private static int counter = 0;

    public static final class Preview {
        private final ResourceLocation front;
        private final ResourceLocation back;
        private final int width;
        private final int height;
        private final boolean failed;

        public Preview(ResourceLocation front, ResourceLocation back, int width, int height, boolean failed) {
            this.front = front;
            this.back = back;
            this.width = width;
            this.height = height;
            this.failed = failed;
        }

        public ResourceLocation front() {
            return front;
        }

        public ResourceLocation back() {
            return back;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public boolean failed() {
            return failed;
        }
    }

    private static final class Pending {
        private final String key;
        private final int[] pixels;
        private final int width;
        private final int height;

        private Pending(String key, int[] pixels, int width, int height) {
            this.key = key;
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }

        String key() {
            return key;
        }

        int[] pixels() {
            return pixels;
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }
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
        Minecraft mc = Minecraft.getMinecraft();
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
                BufferedImage frontImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                BufferedImage backImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = pending.pixels()[y * width + x];
                        frontImage.setRGB(x, y, argb);
                        backImage.setRGB(width - 1 - x, y, argb);
                    }
                }
                ResourceLocation front = mc.getTextureManager()
                    .getDynamicTextureLocation("cnpclines_pvfront_" + counter++,
                        new net.minecraft.client.renderer.texture.DynamicTexture(frontImage));
                ResourceLocation back = mc.getTextureManager()
                    .getDynamicTextureLocation("cnpclines_pvback_" + counter++,
                        new net.minecraft.client.renderer.texture.DynamicTexture(backImage));
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
            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location);
            if (resource != null) {
                InputStream stream = resource.getInputStream();
                try {
                    return readAll(stream);
                } finally {
                    stream.close();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
