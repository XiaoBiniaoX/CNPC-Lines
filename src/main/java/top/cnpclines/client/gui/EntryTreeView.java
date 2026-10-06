package top.cnpclines.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import top.cnpclines.client.CustomSkinsPack;

public class EntryTreeView {

    public interface Listener {
        void onDirChosen();
    }

    static final int ROW_H = 14;

    private static final int T_UP = 1;
    private static final int T_DIR = 2;
    private static final int T_NS = 3;

    private static final int COL_FRAME = 0xFF808080;
    private static final int COL_BG = 0xFFEDEDED;
    private static final int COL_HOVER = 0xFFD5D5D5;
    private static final int COL_SELECTED = 0xFFBFD8F5;
    private static final int COL_CURRENT = 0xFFCDE8CD;
    private static final long DOUBLE_CLICK_MS = 400;

    private record Row(int type, String label, String dir) {
    }

    private final Font font;
    private final List<Row> rows = new ArrayList<>();

    private int x;
    private int y;
    private int w;
    private int h;

    private String rootDir = "";
    private String currentDir = "";
    private String selectedDir;
    private String lastClickId;
    private long lastClickAt;
    private Listener listener;
    private float scroll;
    private int hover = -1;

    public EntryTreeView(Font font) {
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

    public String currentDir() {
        return this.currentDir;
    }

    public String selectedDir() {
        return this.selectedDir;
    }

    public String displayDir() {
        if (this.selectedDir != null && exists(this.selectedDir)) {
            return this.selectedDir;
        }
        return this.currentDir;
    }

    public void configure(String rootDir, String currentDir) {
        this.rootDir = normalize(rootDir);
        this.currentDir = normalize(currentDir);
        this.selectedDir = null;
        this.lastClickId = null;
        rebuild();
    }

    public void restoreSelection(String id) {
        if (id != null && exists(id)) {
            this.selectedDir = id;
        }
    }

    static void openDefault(EntryTreeView tree, String main, String root) {
        String parent = parentOf(normalize(main));
        String parentPath = pathOf(parent);
        if (parentPath != null && parentPath.startsWith(root) && exists(parent)) {
            tree.configure(main, parent);
            tree.restoreSelection(normalize(main));
        } else {
            tree.configure(main, main);
        }
    }

    public void rebuild() {
        if (!exists(this.currentDir)) {
            this.currentDir = exists(this.rootDir) ? this.rootDir : "";
        }
        this.rows.clear();
        String ns = nsOf(this.currentDir);
        if (ns == null) {
            for (String name : ResourceIndex.namespaces()) {
                this.rows.add(new Row(T_NS, name + ":", name + ":"));
            }
        } else {
            String path = pathOf(this.currentDir);
            String parent = parentOf(this.currentDir);
            if (parent != null && !parent.equals(this.currentDir) && exists(parent)) {
                this.rows.add(new Row(T_UP, "↑ 上一级", parent));
            }
            for (String name : ResourceIndex.children(ns, path)) {
                this.rows.add(new Row(T_DIR, name + "/", ns + ":" + path + name + "/"));
            }
        }
        this.scroll = clampScroll(this.scroll);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(this.x, this.y, this.x + this.w, this.y + this.h, COL_FRAME);
        graphics.fill(this.x + 1, this.y + 1, this.x + this.w - 1, this.y + this.h - 1, COL_BG);
        int contentX = this.x + 1;
        int contentY = this.y + 1;
        int contentW = this.w - 2;
        int contentH = this.h - 2;
        int rowW = contentW - 5;

        this.hover = rowAt(mouseX, mouseY);
        this.scroll = clampScroll(this.scroll);

        if (this.rows.isEmpty()) {
            graphics.drawCenteredString(this.font, "（空）",
                contentX + rowW / 2, contentY + contentH / 2 - 4, 0xFF909090);
            return;
        }

        int first = Math.max(0, (int) (this.scroll / ROW_H));
        int last = Math.min(this.rows.size(),
            (int) Math.ceil((this.scroll + contentH) / (float) ROW_H) + 1);
        for (int i = first; i < last; i++) {
            int rowY = contentY + (int) (i * ROW_H - this.scroll);
            if (rowY + ROW_H > contentY + contentH) {
                break;
            }
            int rowBottom = Math.min(rowY + ROW_H, contentY + contentH);
            Row row = this.rows.get(i);
            boolean selectable = row.type == T_DIR || row.type == T_NS || row.type == T_UP;
            if (i == this.hover) {
                graphics.fill(contentX, rowY, contentX + rowW, rowBottom, COL_HOVER);
            } else if (selectable && row.dir.equals(this.selectedDir)) {
                graphics.fill(contentX, rowY, contentX + rowW, rowBottom, COL_SELECTED);
            } else if (selectable && this.currentDir.equals(row.dir)) {
                graphics.fill(contentX, rowY, contentX + rowW, rowBottom, COL_CURRENT);
            }
            int color = rowColor(row);
            int indent = 3;
            int textW = rowW - indent - 2;
            if (textW > 6) {
                graphics.drawString(this.font,
                    TextureEntryList.ellipsize(this.font, row.label, textW),
                    contentX + indent, rowY + 3, color, false);
            }
        }

        if (maxScroll() > 0) {
            int trackX = contentX + contentW - 4;
            graphics.fill(trackX, contentY, trackX + 3, contentY + contentH, 0xFFC8C8C8);
            int thumbH = Math.max(14, (int) ((long) contentH * contentH
                / Math.max(1, this.rows.size() * ROW_H)));
            int max = (int) maxScroll();
            int thumbY = contentY;
            if (max > 0) {
                thumbY = contentY + (int) ((this.scroll / max) * (contentH - thumbH));
            }
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF8E8E8E);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0 || index >= this.rows.size()) {
            return false;
        }
        Row row = this.rows.get(index);
        if (row.type != T_UP && row.type != T_DIR && row.type != T_NS) {
            return true;
        }
        long now = System.currentTimeMillis();
        String id = row.type + ":" + row.dir;
        boolean doubleClick = id.equals(this.lastClickId)
            && now - this.lastClickAt < DOUBLE_CLICK_MS;
        this.lastClickId = doubleClick ? null : id;
        this.lastClickAt = now;
        if (doubleClick) {
            navTo(row.dir);
        } else if (row.type != T_UP) {
            this.selectedDir = row.dir;
            if (this.listener != null) {
                this.listener.onDirChosen();
            }
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

    public boolean hoveringFolder() {
        if (this.hover < 0 || this.hover >= this.rows.size()) {
            return false;
        }
        int type = this.rows.get(this.hover).type;
        return type == T_DIR || type == T_NS || type == T_UP;
    }

    private void navTo(String dir) {
        if (this.currentDir.equals(dir)) {
            return;
        }
        this.currentDir = dir;
        this.selectedDir = null;
        this.scroll = 0;
        rebuild();
        if (this.listener != null) {
            this.listener.onDirChosen();
        }
    }

    private int rowColor(Row row) {
        return switch (row.type) {
            case T_UP -> 0xFF2060A0;
            case T_DIR, T_NS -> this.currentDir.equals(row.dir) ? 0xFF101010 : 0xFF404040;
            default -> 0xFF404040;
        };
    }

    private int rowAt(double mouseX, double mouseY) {
        int contentW = this.w - 2;
        if (mouseX < this.x + 1 || mouseX >= this.x + 1 + contentW - 5
            || mouseY < this.y + 1 || mouseY >= this.y + this.h - 1) {
            return -1;
        }
        int offset = (int) (mouseY - (this.y + 1) + this.scroll);
        if (offset < 0) {
            return -1;
        }
        int index = offset / ROW_H;
        int rowTop = (int) (index * ROW_H - this.scroll);
        if (rowTop < 0 || rowTop + ROW_H > this.h - 2) {
            return -1;
        }
        return index;
    }

    private float maxScroll() {
        return Math.max(0, this.rows.size() * ROW_H - (this.h - 2));
    }

    private float clampScroll(float value) {
        return Math.max(0, Math.min(maxScroll(), value));
    }

    static String normalize(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        if (id.indexOf(':') >= 0) {
            return id;
        }
        return CustomSkinsPack.NAMESPACE + ":" + id;
    }

    static String nsOf(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        int colon = id.indexOf(':');
        return colon <= 0 ? null : id.substring(0, colon);
    }

    static String pathOf(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        int colon = id.indexOf(':');
        return colon < 0 ? null : id.substring(colon + 1);
    }

    static String parentOf(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        int colon = id.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String ns = id.substring(0, colon);
        String path = id.substring(colon + 1);
        if (path.isEmpty()) {
            return "";
        }
        int lastSlash = path.lastIndexOf('/', path.length() - 2);
        if (lastSlash < 0) {
            return ns + ":";
        }
        return ns + ":" + path.substring(0, lastSlash + 1);
    }

    static String displayPath(String id) {
        if (id == null || id.isEmpty()) {
            return "全部命名空间";
        }
        int colon = id.indexOf(':');
        if (colon < 0) {
            return id;
        }
        String ns = id.substring(0, colon);
        String path = id.substring(colon + 1);
        if (path.isEmpty()) {
            return ns + ":";
        }
        if (CustomSkinsPack.NAMESPACE.equals(ns)) {
            return path;
        }
        return id;
    }

    static boolean exists(String id) {
        if (id == null || id.isEmpty()) {
            return true;
        }
        int colon = id.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String ns = id.substring(0, colon);
        String path = id.substring(colon + 1);
        if (path.isEmpty()) {
            return ResourceIndex.namespaces().contains(ns);
        }
        return ResourceIndex.hasDir(ns, path);
    }

    static String idOf(ResourceLocation location) {
        if (location == null) {
            return "";
        }
        return location.getNamespace() + ":" + TextureEntryList.parentDir(location.getPath());
    }
}
