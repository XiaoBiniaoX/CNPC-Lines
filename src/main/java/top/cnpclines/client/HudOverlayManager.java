package top.cnpclines.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class HudOverlayManager {
    private static final Logger LOGGER = LogManager.getLogger("cnpclines");
    private static final int FADE_MS = 700;
    private static final int FADE_IN_MS = 300;
    private static final int SLIDE_PX = 20;
    private static final Set<String> LOGGED_ICONS = new java.util.HashSet<>();
    private static final Map<Integer, HudText> overlays = new HashMap<>();
    private static final Map<Integer, HudIcon> icons = new HashMap<>();
    private static final Map<Integer, HudBackground> backgrounds = new HashMap<>();
    private static final Map<Integer, FadeState> fadeOutTimers = new HashMap<>();
    private static final Map<Integer, Long> fadeInStarts = new HashMap<>();
    private static final Map<ResourceLocation, int[]> textureSizes = new HashMap<>();

    private HudOverlayManager() {
    }

    public static void addOverlayText(int id, String message, int x, int y, String hexColor) {
        int color = hexStringToColor(hexColor);
        overlays.put(id, new HudText(message, x, y, color, 255));
    }

    public static void addIconOverlay(int id, String texturePath, int x, int y, float scale) {
        icons.put(id, new HudIcon(new ResourceLocation(texturePath), x, y, scale, 255));
    }

    public static void addBackgroundOverlay(int id, String texturePath, int x, int y, int width, int height) {
        backgrounds.put(id, new HudBackground(new ResourceLocation(texturePath), x, y, width, height, 255));
    }

    public static void removeComponent(int id) {
        overlays.remove(id);
        icons.remove(id);
        backgrounds.remove(id);
        fadeOutTimers.remove(id);
        fadeInStarts.remove(id);
    }

    public static void fadeInAll() {
        long now = System.currentTimeMillis();
        for (int id = 0; id < 500; id++) {
            if (overlays.containsKey(id) || icons.containsKey(id) || backgrounds.containsKey(id)) {
                fadeInStarts.put(id, now);
            }
        }
    }

    public static void fadeOut(int id, int durationMillis) {
        if (overlays.containsKey(id) || icons.containsKey(id) || backgrounds.containsKey(id)) {
            long now = System.currentTimeMillis();
            long endAt = now + durationMillis;
            long startAt = Math.max(now, endAt - FADE_MS);
            fadeOutTimers.put(id, new FadeState(startAt, endAt));
        }
    }

    public static void fadeOutAll(int durationMillis) {
        for (int id = 0; id < 500; id++) {
            fadeOut(id, durationMillis);
        }
    }

    public static void renderOverlays(GuiGraphics guiGraphics) {
        if (overlays.isEmpty() && icons.isEmpty() && backgrounds.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        List<Integer> componentsToRemove = new ArrayList<>();

        for (Map.Entry<Integer, FadeState> entry : fadeOutTimers.entrySet()) {
            if (currentTime >= entry.getValue().endAt) {
                componentsToRemove.add(entry.getKey());
            }
        }

        for (int id : componentsToRemove) {
            removeComponent(id);
        }
        if (!componentsToRemove.isEmpty()) {
            LOGGER.info("[cnpclines] overlay expire: {}", componentsToRemove);
        }

        Font font = Minecraft.getInstance().font;

        for (Map.Entry<Integer, HudBackground> entry : backgrounds.entrySet()) {
            HudBackground background = entry.getValue();
            int alpha = fadeAlpha(entry.getKey(), currentTime);
            if (alpha <= 0) {
                continue;
            }
            int slide = fadeSlide(entry.getKey(), currentTime);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha / 255.0F);
            int[] backgroundSize = textureSize(background.texture);
            guiGraphics.blit(
                background.texture, background.x, background.y + slide, 0.0F, 0.0F,
                background.width, background.height, backgroundSize[0], backgroundSize[1]
            );
        }

        for (Map.Entry<Integer, HudIcon> entry : icons.entrySet()) {
            HudIcon icon = entry.getValue();
            int alpha = fadeAlpha(entry.getKey(), currentTime);
            if (alpha <= 0) {
                continue;
            }
            int slide = fadeSlide(entry.getKey(), currentTime);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha / 255.0F);
            int[] iconSize = textureSize(icon.texture);
            if (LOGGED_ICONS.add(icon.texture.toString())) {
                LOGGER.info("[cnpclines] icon render: tex={}, size={}x{}, scale={}, final={}px",
                    icon.texture, iconSize[0], iconSize[1], icon.scale, icon.scale * 16.0F);
            }
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate((double) icon.x, (double) (icon.y + slide), 0.0D);
            guiGraphics.pose().scale(icon.scale * 16.0F / iconSize[0], icon.scale * 16.0F / iconSize[1], 1.0F);
            guiGraphics.blit(icon.texture, 0, 0, 0.0F, 0.0F, iconSize[0], iconSize[1], iconSize[0], iconSize[1]);
            guiGraphics.pose().popPose();
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        for (Map.Entry<Integer, HudText> entry : overlays.entrySet()) {
            HudText overlay = entry.getValue();
            int alpha = fadeAlpha(entry.getKey(), currentTime);
            if (alpha <= 0) {
                continue;
            }
            int slide = fadeSlide(entry.getKey(), currentTime);
            guiGraphics.drawString(
                font, Component.literal(overlay.message), overlay.x, overlay.y + slide,
                overlay.color & 16777215 | alpha << 24, false
            );
        }
    }

    private static int fadeAlpha(int id, long now) {
        int alpha = 255;
        Long fadeInStart = fadeInStarts.get(id);
        if (fadeInStart != null) {
            long fadeInEnd = fadeInStart + FADE_IN_MS;
            if (now < fadeInStart) {
                alpha = 0;
            } else if (now < fadeInEnd) {
                alpha = (int) ((now - fadeInStart) / (float) FADE_IN_MS * 255.0F);
            }
        }
        FadeState state = fadeOutTimers.get(id);
        if (state == null) {
            return alpha;
        }
        int outAlpha;
        if (now < state.startAt) {
            outAlpha = 255;
        } else {
            long span = state.endAt - state.startAt;
            if (span <= 0L) {
                outAlpha = 0;
            } else {
                outAlpha = (int) ((state.endAt - now) / (float) span * 255.0F);
            }
        }
        return Math.min(alpha, outAlpha);
    }

    private static int fadeSlide(int id, long now) {
        FadeState state = fadeOutTimers.get(id);
        if (state == null || now < state.startAt) {
            return 0;
        }
        long span = state.endAt - state.startAt;
        if (span <= 0L) {
            return SLIDE_PX;
        }
        float progress = (now - state.startAt) / (float) span;
        return (int) (progress * SLIDE_PX);
    }

    private static int hexStringToColor(String hex) {
        return Integer.parseUnsignedInt(hex.replace("#", ""), 16);
    }

    private static int[] textureSize(ResourceLocation texture) {
        int[] cached = textureSizes.get(texture);
        if (cached != null) {
            return cached;
        }
        int[] size = {16, 16};
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().open()) {
                    byte[] head = stream.readNBytes(24);
                    if (head.length == 24 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
                        int width = ((head[16] & 0xFF) << 24) | ((head[17] & 0xFF) << 16) | ((head[18] & 0xFF) << 8) | (head[19] & 0xFF);
                        int height = ((head[20] & 0xFF) << 24) | ((head[21] & 0xFF) << 16) | ((head[22] & 0xFF) << 8) | (head[23] & 0xFF);
                        if (width > 0 && height > 0) {
                            size = new int[] {width, height};
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (size[0] == 16 && size[1] == 16) {
            AbstractTexture abstractTexture = Minecraft.getInstance().getTextureManager().getTexture(texture);
            if (abstractTexture instanceof DynamicTexture dynamicTexture && dynamicTexture.getPixels() != null) {
                int width = dynamicTexture.getPixels().getWidth();
                int height = dynamicTexture.getPixels().getHeight();
                if (width > 0 && height > 0) {
                    size = new int[] {width, height};
                }
            }
        }
        textureSizes.put(texture, size);
        return size;
    }

    private static class FadeState {
        final long startAt;
        final long endAt;

        FadeState(long startAt, long endAt) {
            this.startAt = startAt;
            this.endAt = endAt;
        }
    }

    private static class HudBackground {
        ResourceLocation texture;
        int x;
        int y;
        int width;
        int height;
        int alpha;

        HudBackground(ResourceLocation texture, int x, int y, int width, int height, int alpha) {
            this.texture = texture;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.alpha = alpha;
        }
    }

    private static class HudIcon {
        ResourceLocation texture;
        int x;
        int y;
        float scale;
        int alpha;

        HudIcon(ResourceLocation texture, int x, int y, float scale, int alpha) {
            this.texture = texture;
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.alpha = alpha;
        }
    }

    private static class HudText {
        String message;
        int x;
        int y;
        int color;
        int alpha;

        HudText(String message, int x, int y, int color, int alpha) {
            this.message = message;
            this.x = x;
            this.y = y;
            this.color = color;
            this.alpha = alpha;
        }
    }
}
