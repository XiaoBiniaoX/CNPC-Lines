package top.cnpclines.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class TextureEntryList {

    public interface Listener {
        void onActivate(ResourceLocation location);

        void onDelete(ResourceLocation location);

        default void onSelect(ResourceLocation location) {
        }
    }

    public interface SkinThumbRenderer {
        void drawSkinThumb(GuiGraphics graphics, ResourceLocation location,
            int x, int y, int size, boolean back);
    }

    static final int ROW_H = 26;

    private static final int COL_FRAME = 0xFF808080;
    private static final int COL_BG = 0xFFEDEDED;
    private static final int COL_ROW = 0xFFF6F6F6;
    private static final int COL_ROW_HOVER = 0xFFDCDCDC;
    private static final int COL_ROW_CURRENT = 0xFFBBD8F5;
    private static final int COL_ROW_LINE = 0xFFC8C8C8;
    private static final int COL_ROW_SELECTED = 0xFFE0A040;
    private static final int COL_NAME = 0xFF303030;

    static final int[][] CAPE_SLICES = {
        {12, 1, 10, 16},
        {1, 1, 10, 16},
        {36, 2, 10, 20},
        {24, 2, 10, 20},
    };

    private final Font font;
    private final SwipeDragController swipe = new SwipeDragController();

    private int x;
    private int y;
    private int w;
    private int h;

    private final List<ResourceLocation> items = new ArrayList<>();
    private ResourceLocation highlight;
    private int previewMode;
    private SkinThumbRenderer skinThumb;
    private boolean swipeEnabled;
    private Listener listener;

    private float scroll;
    private int hover = -1;
    private int dragIndex = -1;
    private int selected = -1;
    private boolean draggingThumb;

    public TextureEntryList(Font font) {
        this.font = font;
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPreviewMode(int previewMode) {
        this.previewMode = previewMode;
    }

    public void setSkinThumbRenderer(SkinThumbRenderer skinThumb) {
        this.skinThumb = skinThumb;
    }

    public void setSwipeEnabled(boolean swipeEnabled) {
        this.swipeEnabled = swipeEnabled;
    }

    public void setHighlight(ResourceLocation location) {
        this.highlight = location;
    }

    public void selectLocation(ResourceLocation location) {
        int index = location == null ? -1 : this.items.indexOf(location);
        this.selected = index;
        if (index >= 0) {
            float top = index * ROW_H;
            float bottom = top + ROW_H;
            float contentH = this.h - 2;
            if (top < this.scroll) {
                this.scroll = top;
            } else if (bottom > this.scroll + contentH) {
                this.scroll = bottom - contentH;
            }
            this.scroll = clampScroll(this.scroll);
        }
    }

    public void setItems(List<ResourceLocation> locations) {
        this.items.clear();
        if (locations != null) {
            this.items.addAll(locations);
        }
        this.scroll = clampScroll(this.scroll);
        this.hover = -1;
        this.dragIndex = -1;
        this.selected = -1;
    }

    public ResourceLocation selectedLocation() {
        if (this.selected >= 0 && this.selected < this.items.size()) {
            return this.items.get(this.selected);
        }
        return null;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(this.x, this.y, this.x + this.w, this.y + this.h, COL_FRAME);
        graphics.fill(this.x + 1, this.y + 1, this.x + this.w - 1, this.y + this.h - 1, COL_BG);
        int contentX = this.x + 1;
        int contentY = this.y + 1;
        int contentW = this.w - 2;
        int contentH = this.h - 2;
        int rowW = contentW - 7;

        this.hover = rowAt(mouseX, mouseY);
        int current = this.highlight == null ? -1 : this.items.indexOf(this.highlight);

        if (this.items.isEmpty()) {
            graphics.drawCenteredString(this.font, "（暂无文件）",
                contentX + rowW / 2, contentY + contentH / 2 - 4, 0xFF909090);
            return;
        }

        int first = Math.max(0, (int) (this.scroll / ROW_H));
        int last = Math.min(this.items.size(),
            (int) Math.ceil((this.scroll + contentH) / (float) ROW_H) + 1);
        for (int i = first; i < last; i++) {
            int rowY = contentY + (int) (i * ROW_H - this.scroll);
            int rowBottom = Math.min(rowY + ROW_H, contentY + contentH);
            if (rowY + ROW_H > contentY + contentH) {
                break;
            }
            boolean dragging = this.swipeEnabled && i == this.dragIndex
                && this.swipe.state() == SwipeDragController.State.DELETE;
            int offset = dragging ? (int) this.swipe.deltaX() : 0;
            if (dragging) {
                int alpha = Math.min(170, 45 + (int) Math.abs(this.swipe.deltaX()));
                graphics.fill(contentX, rowY, contentX + rowW, rowBottom, (alpha << 24) | 0x00D52B2B);
            } else if (i == current) {
                graphics.fill(contentX, rowY, contentX + rowW, rowBottom, COL_ROW_CURRENT);
            } else if (i == this.hover) {
                graphics.fill(contentX, rowY, contentX + rowW, rowBottom, COL_ROW_HOVER);
            } else {
                graphics.fill(contentX, rowY, contentX + rowW, rowBottom, COL_ROW);
            }
            graphics.fill(contentX, rowBottom - 1, contentX + rowW, rowBottom, COL_ROW_LINE);
            if (i == this.selected) {
                graphics.fill(contentX, rowY, contentX + rowW, rowY + 1, COL_ROW_SELECTED);
                graphics.fill(contentX, rowBottom - 1, contentX + rowW, rowBottom, COL_ROW_SELECTED);
                graphics.fill(contentX, rowY, contentX + 1, rowBottom, COL_ROW_SELECTED);
                graphics.fill(contentX + rowW - 1, rowY, contentX + rowW, rowBottom, COL_ROW_SELECTED);
            }

            ResourceLocation location = this.items.get(i);
            boolean npcSlots = this.previewMode == 0 && this.skinThumb != null;
            int slots;
            if (npcSlots) {
                slots = 2;
            } else if (this.previewMode == 1) {
                TexturePreviewLoader.Preview preview = TexturePreviewLoader.peek(location);
                boolean capeLayout = preview != null && !preview.failed()
                    && preview.front() != null
                    && preview.width() == 64 && preview.height() == 32;
                slots = capeLayout ? CAPE_SLICES.length : 1;
            } else {
                slots = 1;
            }
            if (npcSlots) {
                drawSlotFrame(graphics, contentX + 1 + offset, rowY + 1);
                drawSlotFrame(graphics, contentX + 27 + offset, rowY + 1);
                this.skinThumb.drawSkinThumb(graphics, location,
                    contentX + 1 + offset, rowY + 1, 24, false);
                this.skinThumb.drawSkinThumb(graphics, location,
                    contentX + 27 + offset, rowY + 1, 24, true);
            } else if (slots == CAPE_SLICES.length) {
                for (int s = 0; s < CAPE_SLICES.length; s++) {
                    drawSlice(graphics, this.font, contentX + 1 + s * 26 + offset, rowY + 1,
                        location, CAPE_SLICES[s]);
                }
            } else {
                drawThumb(graphics, this.font, contentX + 1 + offset, rowY + 1, location);
            }
            int nameX = contentX + 1 + slots * 26 + offset;
            int nameW = contentX + rowW - 3 - nameX;
            if (nameW > 8) {
                graphics.drawString(this.font, ellipsize(this.font, fileName(location), nameW),
                    nameX, rowY + 9, COL_NAME, false);
            }
        }

        if (maxScroll() > 0) {
            drawScrollbar(graphics, contentX, contentY, contentW, contentH);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        int contentW = this.w - 2;
        int contentH = this.h - 2;
        if (maxScroll() > 0 && mouseX >= this.x + 1 + contentW - 7) {
            this.draggingThumb = true;
            updateThumbDrag(mouseY, contentH);
            return true;
        }
        int index = rowAt(mouseX, mouseY);
        if (index >= 0 && index < this.items.size()) {
            if (this.swipeEnabled) {
                this.dragIndex = index;
                this.swipe.begin(mouseX, mouseY, System.currentTimeMillis());
            } else {
                activate(index);
            }
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) {
            return false;
        }
        if (this.draggingThumb) {
            updateThumbDrag(mouseY, this.h - 2);
            return true;
        }
        if (this.swipe.active()) {
            this.swipe.move(mouseX, mouseY);
            this.swipe.update(System.currentTimeMillis());
            this.swipe.chooseGesture();
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (this.draggingThumb) {
            this.draggingThumb = false;
            return true;
        }
        if (this.swipe.active()) {
            boolean deleted = this.swipe.state() == SwipeDragController.State.DELETE
                && this.swipe.deltaX() <= -SwipeDragController.DELETE_THRESHOLD;
            boolean tapped = Math.abs(this.swipe.deltaX()) < 4 && Math.abs(this.swipe.deltaY()) < 4;
            int index = this.dragIndex;
            this.swipe.end();
            this.dragIndex = -1;
            if (index >= 0 && index < this.items.size()) {
                if (deleted) {
                    if (this.listener != null) {
                        this.listener.onDelete(this.items.get(index));
                    }
                } else if (tapped) {
                    activate(index);
                }
            }
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        this.scroll = clampScroll(this.scroll - (float) (delta * ROW_H));
        return true;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.w
            && mouseY >= this.y && mouseY < this.y + this.h;
    }

    private void activate(int index) {
        if (this.listener != null && index >= 0 && index < this.items.size()) {
            this.selected = index;
            this.listener.onSelect(this.items.get(index));
            this.listener.onActivate(this.items.get(index));
        }
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < this.x + 1 || mouseX >= this.x + this.w - 8
            || mouseY < this.y + 1 || mouseY >= this.y + this.h - 1) {
            return -1;
        }
        int offset = (int) (mouseY - (this.y + 1) + this.scroll);
        if (offset < 0) {
            return -1;
        }
        return offset / ROW_H;
    }

    private float maxScroll() {
        return Math.max(0, this.items.size() * ROW_H - (this.h - 2));
    }

    private float clampScroll(float value) {
        return Math.max(0, Math.min(maxScroll(), value));
    }

    private int thumbHeight() {
        int contentH = this.h - 2;
        int total = this.items.size() * ROW_H;
        if (total <= 0) {
            return contentH;
        }
        return Math.max(16, (int) ((long) contentH * contentH / total));
    }

    private void drawScrollbar(GuiGraphics graphics, int contentX, int contentY, int contentW, int contentH) {
        int trackX = contentX + contentW - 6;
        graphics.fill(trackX, contentY, trackX + 5, contentY + contentH, 0xFFC8C8C8);
        int thumbH = thumbHeight();
        int max = (int) maxScroll();
        int thumbY = contentY;
        if (max > 0) {
            thumbY = contentY + (int) ((this.scroll / max) * (contentH - thumbH));
        }
        graphics.fill(trackX, thumbY, trackX + 5, thumbY + thumbH,
            this.draggingThumb ? 0xFF6E6E6E : 0xFF8E8E8E);
    }

    private void updateThumbDrag(double mouseY, int contentH) {
        int thumbH = thumbHeight();
        int max = (int) maxScroll();
        if (max <= 0 || contentH <= thumbH) {
            return;
        }
        double fraction = (mouseY - (this.y + 1) - thumbH / 2.0) / (contentH - thumbH);
        this.scroll = clampScroll((float) (fraction * max));
    }

    static void drawSlotFrame(GuiGraphics graphics, int sx, int sy) {
        int size = 24;
        graphics.fill(sx, sy, sx + size, sy + size, 0xFF3A3A3A);
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                int cell = ((row + col) % 2 == 0) ? 0xFF8B8B8B : 0xFF9E9E9E;
                graphics.fill(sx + 2 + col * 4, sy + 2 + row * 4, sx + 6 + col * 4, sy + 6 + row * 4, cell);
            }
        }
    }

    static void drawThumb(GuiGraphics graphics, Font font, int sx, int sy, ResourceLocation location) {
        int size = 24;
        drawSlotFrame(graphics, sx, sy);
        if (location == null) {
            return;
        }
        TexturePreviewLoader.Preview preview = TexturePreviewLoader.peek(location);
        if (preview == null) {
            graphics.drawCenteredString(font, "...", sx + size / 2, sy + 9, 0xFFFFFFFF);
            return;
        }
        if (preview.failed() || preview.front() == null) {
            graphics.drawCenteredString(font, "无", sx + size / 2, sy + 9, 0xFFFFFFFF);
            return;
        }
        int texWidth = preview.width();
        int texHeight = preview.height();
        float scale = Math.min(20.0f / texWidth, 20.0f / texHeight);
        int drawWidth = Math.max(1, Math.round(texWidth * scale));
        int drawHeight = Math.max(1, Math.round(texHeight * scale));
        graphics.blit(preview.front(), sx + 2 + (20 - drawWidth) / 2, sy + 2 + (20 - drawHeight) / 2,
            drawWidth, drawHeight, 0.0F, 0.0F, texWidth, texHeight, texWidth, texHeight);
    }

    static void drawSlice(GuiGraphics graphics, Font font, int sx, int sy, ResourceLocation location, int[] slice) {
        int size = 24;
        drawSlotFrame(graphics, sx, sy);
        if (location == null) {
            return;
        }
        TexturePreviewLoader.Preview preview = TexturePreviewLoader.peek(location);
        if (preview == null) {
            graphics.drawCenteredString(font, "...", sx + size / 2, sy + 9, 0xFFFFFFFF);
            return;
        }
        if (preview.failed() || preview.front() == null) {
            graphics.drawCenteredString(font, "无", sx + size / 2, sy + 9, 0xFFFFFFFF);
            return;
        }
        int sliceWidth = slice[2];
        int sliceHeight = slice[3];
        float scale = Math.min(20.0f / sliceWidth, 20.0f / sliceHeight);
        int drawWidth = Math.max(1, Math.round(sliceWidth * scale));
        int drawHeight = Math.max(1, Math.round(sliceHeight * scale));
        graphics.blit(preview.front(),
            sx + 2 + (20 - drawWidth) / 2, sy + 2 + (20 - drawHeight) / 2,
            drawWidth, drawHeight, (float) slice[0], (float) slice[1],
            sliceWidth, sliceHeight, preview.width(), preview.height());
    }

    static String fileName(ResourceLocation location) {
        if (location == null) {
            return "";
        }
        String original = ResourceIndex.toOriginal(location.getNamespace(), location.getPath());
        int index = original.lastIndexOf('/');
        return index < 0 ? original : original.substring(index + 1);
    }

    static String parentDir(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? "" : path.substring(0, index + 1);
    }

    static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String next = builder.toString() + text.charAt(i) + "...";
            if (font.width(next) > maxWidth) {
                break;
            }
            builder.append(text.charAt(i));
        }
        return builder.length() == 0 ? "..." : builder.append("...").toString();
    }
}
