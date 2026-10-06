package top.cnpclines.client;

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
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;

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
            REGISTERED.put(skinKey, "");
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
        try {
            ResourceLocation location = new ResourceLocation(path);
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

    private static int[] computeHead8x8(byte[] png) {
        try {
            BufferedImage skin = ImageIO.read(new ByteArrayInputStream(png));
            if (skin == null || skin.getWidth() < 16 || skin.getHeight() < 16) {
                return null;
            }
            boolean hasHat = skin.getWidth() >= 48 && skin.getHeight() >= 16;
            int[] head = new int[64];
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int base = skin.getRGB(8 + x, 8 + y);
                    if (hasHat) {
                        int hat = skin.getRGB(40 + x, 8 + y);
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
