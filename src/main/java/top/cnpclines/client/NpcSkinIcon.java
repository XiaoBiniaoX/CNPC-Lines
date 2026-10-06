package top.cnpclines.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import top.cnpclines.client.gui.ResourceIndex;

public final class NpcSkinIcon {
    private static final Map<String, String> REGISTERED = new HashMap<>();
    private static final Set<String> INFLIGHT = new HashSet<>();
    private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<>();
    private static int counter = 0;

    private record Pending(String skinKey, int[] head) {
    }

    private NpcSkinIcon() {
    }

    public static String peek(EntityNPCInterface npc) {
        String skinKey = skinKey(npc);
        if (skinKey == null) {
            return "";
        }
        String registered = REGISTERED.get(skinKey);
        if (registered != null) {
            return registered;
        }
        if (INFLIGHT.contains(skinKey)) {
            return "";
        }
        byte[] png = readSkinPng(skinKey);
        if (png == null) {
            return "";
        }
        INFLIGHT.add(skinKey);
        CompletableFuture.runAsync(() -> {
            int[] head = computeHead8x8(png);
            PENDING.add(new Pending(skinKey, head));
        }).whenComplete((unused, error) -> {
            if (error != null) {
                PENDING.add(new Pending(skinKey, null));
            }
        });
        return "";
    }

    public static boolean isInflight(EntityNPCInterface npc) {
        String skinKey = skinKey(npc);
        return skinKey != null && INFLIGHT.contains(skinKey);
    }

    public static void drain() {
        if (PENDING.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Pending pending;
        while ((pending = PENDING.poll()) != null) {
            INFLIGHT.remove(pending.skinKey());
            if (pending.head() == null) {
                REGISTERED.put(pending.skinKey(), "");
                continue;
            }
            try {
                NativeImage image = new NativeImage(8, 8, false);
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        int argb = pending.head()[y * 8 + x];
                        int alpha = argb >>> 24;
                        int red = (argb >> 16) & 0xFF;
                        int green = (argb >> 8) & 0xFF;
                        int blue = argb & 0xFF;
                        image.setPixelRGBA(x, y, (alpha << 24) | (blue << 16) | (green << 8) | red);
                    }
                }
                DynamicTexture texture = new DynamicTexture(image);
                ResourceLocation rl = mc.getTextureManager()
                    .register("cnpclines_npc_" + counter++, texture);
                REGISTERED.put(pending.skinKey(), rl.toString());
            } catch (Exception e) {
                REGISTERED.put(pending.skinKey(), "");
            }
        }
    }

    private static String skinKey(EntityNPCInterface npc) {
        try {
            DataDisplay display = npc.display;
            return display.getSkinTexture();
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readSkinPng(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            byte[] weird = ResourceIndex.weirdBytes(path);
            if (weird != null) {
                return weird;
            }
            byte[] png = readResource(path);
            if (png != null) {
                return png;
            }
            byte[] fromDir = readFirstInDirectory(path);
            if (fromDir != null) {
                return fromDir;
            }
            return readMirror(path);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static byte[] readResource(String path) {
        try {
            ResourceLocation location = ResourceLocation.parse(path);
            Minecraft mc = Minecraft.getInstance();
            Optional<Resource> resource = mc.getResourceManager().getResource(location);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().open()) {
                    return stream.readAllBytes();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static byte[] readFirstInDirectory(String path) {
        try {
            String dir = path.endsWith("/") ? path : path + "/";
            int colon = dir.indexOf(':');
            String dirPath = colon >= 0 ? dir.substring(colon + 1) : dir;
            String[] namespaces = colon >= 0
                ? new String[] {dir.substring(0, colon)}
                : new String[] {"minecraft", CustomSkinsPack.NAMESPACE};
            for (String ns : namespaces) {
                List<ResourceLocation> children = ResourceIndex.filesIn(ns, dirPath);
                for (ResourceLocation child : children) {
                    byte[] bytes = readSkinPng(child.toString());
                    if (bytes != null) {
                        return bytes;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static byte[] readMirror(String path) {
        try {
            int colon = path.indexOf(':');
            String ns = colon >= 0 ? path.substring(0, colon) : CustomSkinsPack.NAMESPACE;
            String rel = colon >= 0 ? path.substring(colon + 1) : path;
            Path file = CustomSkinsPack.mirror(ns, rel);
            if (Files.isRegularFile(file)) {
                return Files.readAllBytes(file);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int[] computeHead8x8(byte[] png) {
        try (NativeImage skin = NativeImage.read(png)) {
            int width = skin.getWidth();
            int height = skin.getHeight();
            if (width < 16 || height < 16) {
                return null;
            }
            boolean hasHat = width >= 48;
            int[] head = new int[64];
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int base = toArgb(skin.getPixelRGBA(8 + x, 8 + y));
                    if (hasHat) {
                        int hat = toArgb(skin.getPixelRGBA(40 + x, 8 + y));
                        base = alphaOver(base, hat);
                    }
                    head[y * 8 + x] = base;
                }
            }
            return head;
        } catch (Exception e) {
            return null;
        }
    }

    private static int toArgb(int abgr) {
        return (abgr & 0xFF000000) | ((abgr & 0xFF) << 16)
            | (abgr & 0xFF00) | ((abgr >>> 16) & 0xFF);
    }

    private static int alphaOver(int under, int over) {
        int alphaOver = over >>> 24;
        if (alphaOver == 0) {
            return under;
        }
        if (alphaOver == 255) {
            return over;
        }
        int alphaUnder = under >>> 24;
        int alphaOut = alphaOver + alphaUnder * (255 - alphaOver) / 255;
        if (alphaOut == 0) {
            return 0;
        }
        int blend = (255 - alphaOver) * alphaUnder / 255;
        int red = ((over & 0xFF) * alphaOver + (under & 0xFF) * blend) / alphaOut;
        int green = (((over >> 8) & 0xFF) * alphaOver + ((under >> 8) & 0xFF) * blend) / alphaOut;
        int blue = (((over >> 16) & 0xFF) * alphaOver + ((under >> 16) & 0xFF) * blend) / alphaOut;
        return (alphaOut << 24) | (blue << 16) | (green << 8) | red;
    }
}
