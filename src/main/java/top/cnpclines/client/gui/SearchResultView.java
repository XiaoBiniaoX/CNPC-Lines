package top.cnpclines.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

public class SearchResultView {

    public interface Listener {
        void onLocate(ResourceLocation location, String dirId);
    }

    public static final class Row {

        public final ResourceLocation location;
        public final String dirId;
        public final String name;
        public final String path;

        public Row(ResourceLocation location, String dirId, String name, String path) {
            this.location = location;
            this.dirId = dirId;
            this.name = name;
            this.path = path;
        }
    }

    static final int ROW_H = 28;
    static final int HEADER_H = 14;

    private static final int COL_FRAME = 0xFF808080;
    private static final int COL_BG = 0xFFEDEDED;
    private static final int COL_ROW = 0xFFF6F6F6;
    private static final int COL_ROW_HOVER = 0xFFDCDCDC;
    private static final int COL_ROW_CURRENT = 0xFFBBD8F5;
    private static final int COL_LINE = 0xFFC8C8C8;
    private static final int COL_NAME = 0xFF303030;
    private static final int COL_PATH = 0xFF808080;
    private static final int COL_HEADER = 0xFF606060;

    private final FontRenderer font;
    private final List<Row> rows = new ArrayList<>();

    private int x;
    private int y;
    private int w;
    private int h;

    private float scroll;
    private int hover = -1;
    private ResourceLocation highlight;
    private Listener listener;
    private String scope = "";
    private int previewMode;
    private TextureEntryList.SkinThumbRenderer skinThumb;

    public SearchResultView(FontRenderer font) {
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

    public void setHighlight(ResourceLocation location) {
        this.highlight = location;
    }

    public void setScope(String scope) {
        this.scope = scope == null ? "" : scope;
    }

    public void setPreviewMode(int previewMode) {
        this.previewMode = previewMode;
    }

    public void setSkinThumbRenderer(TextureEntryList.SkinThumbRenderer skinThumb) {
        this.skinThumb = skinThumb;
    }

    public void setResults(List<Row> results) {
        this.rows.clear();
        if (results != null) {
            this.rows.addAll(results);
        }
        this.scroll = 0;
        this.hover = -1;
    }

    public boolean isEmpty() {
        return this.rows.isEmpty();
    }

    public Row first() {
        return this.rows.isEmpty() ? null : this.rows.get(0);
    }

    public void render(int mouseX, int mouseY) {
        Gui.drawRect(this.x, this.y, this.x + this.w, this.y + this.h, COL_FRAME);
        Gui.drawRect(this.x + 1, this.y + 1, this.x + this.w - 1, this.y + this.h - 1, COL_BG);
        int contentX = this.x + 1;
        int contentW = this.w - 2;

        String header;
        if (this.rows.isEmpty()) {
            header = this.scope.isEmpty()
                ? "无匹配结果（试试拼音或首字母）"
                : "在 " + this.scope + " 内无匹配结果";
        } else {
            header = this.scope.isEmpty()
                ? "结果 " + this.rows.size() + " 项，点击或回车定位"
                : "在 " + this.scope + " 内找到 " + this.rows.size()
                    + " 项，点击定位";
        }
        this.font.drawString(TextureEntryList.ellipsize(this.font, header, contentW - 8),
            contentX + 4, this.y + 4, COL_HEADER, false);

        int bodyY = this.y + HEADER_H;
        int bodyH = this.h - HEADER_H - 1;
        if (this.rows.isEmpty()) {
            return;
        }
        if (mouseX >= contentX && mouseX < contentX + contentW
            && mouseY >= bodyY && mouseY < bodyY + bodyH) {
            this.hover = bodyRowAt(mouseX, mouseY);
        } else {
            this.hover = -1;
        }
        this.scroll = clampScroll(this.scroll);

        int first = Math.max(0, (int) (this.scroll / ROW_H));
        int last = Math.min(this.rows.size(),
            (int) Math.ceil((this.scroll + bodyH) / (float) ROW_H) + 1);
        for (int i = first; i < last; i++) {
            int rowY = bodyY + (int) (i * ROW_H - this.scroll);
            if (rowY + ROW_H > bodyY + bodyH) {
                break;
            }
            int rowBottom = rowY + ROW_H;
            Row row = this.rows.get(i);
            if (i == this.hover) {
                Gui.drawRect(contentX, rowY, contentX + contentW, rowBottom, COL_ROW_HOVER);
            } else if (row.location.equals(this.highlight)) {
                Gui.drawRect(contentX, rowY, contentX + contentW, rowBottom, COL_ROW_CURRENT);
            } else {
                Gui.drawRect(contentX, rowY, contentX + contentW, rowBottom, COL_ROW);
            }
            Gui.drawRect(contentX, rowBottom - 1, contentX + contentW, rowBottom, COL_LINE);

            ResourceLocation location = row.location;
            boolean npcSlots = this.previewMode == 0 && this.skinThumb != null;
            int slots;
            if (npcSlots) {
                slots = 2;
            } else if (this.previewMode == 1) {
                TexturePreviewLoader.Preview preview = TexturePreviewLoader.peek(location);
                boolean capeLayout = preview != null && !preview.failed()
                    && preview.front() != null
                    && preview.width() == 64 && preview.height() == 32;
                slots = capeLayout ? TextureEntryList.CAPE_SLICES.length : 1;
            } else {
                slots = 1;
            }
            int thumbY = rowY + 2;
            if (npcSlots) {
                TextureEntryList.drawSlotFrame(contentX + 3, thumbY);
                TextureEntryList.drawSlotFrame(contentX + 29, thumbY);
                this.skinThumb.drawSkinThumb(location, contentX + 3, thumbY, 24, false);
                this.skinThumb.drawSkinThumb(location, contentX + 29, thumbY, 24, true);
            } else if (slots == TextureEntryList.CAPE_SLICES.length) {
                for (int s = 0; s < TextureEntryList.CAPE_SLICES.length; s++) {
                    TextureEntryList.drawSlice(this.font,
                        contentX + 3 + s * 26, thumbY,
                        location, TextureEntryList.CAPE_SLICES[s]);
                }
            } else {
                TextureEntryList.drawThumb(this.font, contentX + 3, thumbY, location);
            }

            int nameX = contentX + 3 + slots * 26 + 4;
            int textW = contentX + contentW - 6 - nameX;
            if (textW > 8) {
                this.font.drawString(TextureEntryList.ellipsize(this.font, row.name, textW),
                    nameX, rowY + 6, COL_NAME, false);
                this.font.drawString(TextureEntryList.ellipsize(this.font, row.path, textW),
                    nameX, rowY + 16, COL_PATH, false);
            }
        }

        int max = (int) maxScroll();
        if (max > 0) {
            int trackX = contentX + contentW - 5;
            Gui.drawRect(trackX, bodyY, trackX + 4, bodyY + bodyH, 0xFFC8C8C8);
            int thumbH = Math.max(14, (int) ((long) bodyH * bodyH
                / Math.max(1, this.rows.size() * ROW_H)));
            int thumbY = bodyY + (int) ((this.scroll / max) * (bodyH - thumbH));
            Gui.drawRect(trackX, thumbY, trackX + 4, thumbY + thumbH, 0xFF8E8E8E);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        int index = bodyRowAt(mouseX, mouseY);
        if (index >= 0 && index < this.rows.size() && this.listener != null) {
            Row row = this.rows.get(index);
            this.listener.onLocate(row.location, row.dirId);
        }
        return true;
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

    private int bodyRowAt(double mouseX, double mouseY) {
        int contentW = this.w - 2;
        int bodyY = this.y + HEADER_H;
        int bodyH = this.h - HEADER_H - 1;
        if (mouseX < this.x + 1 || mouseX >= this.x + contentW
            || mouseY < bodyY || mouseY >= bodyY + bodyH) {
            return -1;
        }
        int offset = (int) (mouseY - bodyY + this.scroll);
        if (offset < 0) {
            return -1;
        }
        int index = offset / ROW_H;
        int rowTop = (int) (index * ROW_H - this.scroll);
        if (rowTop < 0 || rowTop + ROW_H > bodyH) {
            return -1;
        }
        return index;
    }

    private float maxScroll() {
        int bodyH = this.h - HEADER_H - 1;
        return Math.max(0, this.rows.size() * ROW_H - bodyH);
    }

    private float clampScroll(float value) {
        return Math.max(0, Math.min(maxScroll(), value));
    }
}
