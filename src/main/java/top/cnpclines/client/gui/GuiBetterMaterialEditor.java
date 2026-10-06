package top.cnpclines.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.gui.custom.components.CustomGuiEntityDisplay;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import noppes.npcs.shared.client.gui.components.GuiSliderNop;
import noppes.npcs.shared.client.gui.listeners.ISliderListener;
import top.cnpclines.client.CustomSkinsPack;

public class GuiBetterMaterialEditor extends Screen implements ISliderListener {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 232;
    private static final int TAB_H = 17;

    private static final int COL_BG = 0xFFC6C6C6;
    private static final int COL_LIGHT = 0xFFFFFFFF;
    private static final int COL_DARK = 0xFF555555;
    private static final int COL_TEXT = 0xFF404040;
    private static final int COL_HINT = 0xFFA04000;
    private static final int COL_DIVIDER = 0xFF808080;
    private static final int COL_TAB_OFF = 0xFFA0A0A0;
    private static final int COL_SLOT_A = 0xFF8B8B8B;
    private static final int COL_SLOT_B = 0xFF9E9E9E;
    private static final int COL_SLOT_EDGE = 0xFF373737;

    private static final String[] TAB_NAMES = {"材质", "披风", "附加材质"};
    private static final String MANAGER_TAB = "Skins管理器";
    private static final String[] MAIN_PATHS = {
        "textures/entity/humanmale/",
        "textures/cloak/",
        "textures/overlays/",
    };
    private static final String[] ROOT_PATHS = {
        "textures/entity/",
        "textures/cloak/",
        "textures/overlays/",
    };
    private static final ResourceLocation[] TAB_ICONS = {
        ResourceLocation.fromNamespaceAndPath("cnpclines", "textures/overlay/message/icons/skins.png"),
        ResourceLocation.fromNamespaceAndPath("cnpclines", "textures/overlay/message/icons/cloak.png"),
        ResourceLocation.fromNamespaceAndPath("cnpclines", "textures/overlay/message/icons/overlays.png"),
        ResourceLocation.fromNamespaceAndPath("cnpclines", "textures/overlay/message/icons/management.png"),
    };

    private final GuiNpcDisplay parent;
    private final EntityNPCInterface npc;
    private final DataDisplay display;

    private final byte snapshotSkinType;
    private final String snapshotSkin;
    private final String snapshotSkinUrl;
    private final String snapshotCape;
    private final String snapshotOverlay;

    private int guiLeft;
    private int guiTop;

    private int tab;
    private int rotation;
    private boolean derived;
    private boolean closed;
    private boolean suspended;

    private EntryTreeView tree;
    private TextureEntryList list;
    private GuiSliderNop slider;
    private EditBox textureBox;
    private String boxMessage = "";

    public GuiBetterMaterialEditor(GuiNpcDisplay parent) {
        super(Component.literal("更好的材质编辑"));
        this.parent = parent;
        this.npc = parent.npc;
        this.display = npc.display;
        this.snapshotSkinType = display.skinType;
        this.snapshotSkin = display.getSkinTexture();
        String skinUrl = display.getSkinUrl();
        this.snapshotSkinUrl = skinUrl == null ? "" : skinUrl;
        this.snapshotCape = display.getCapeTexture();
        this.snapshotOverlay = display.getOverlayTexture();
    }

    @Override
    protected void init() {
        this.suspended = false;
        this.guiLeft = (this.width - PANEL_W) / 2;
        this.guiTop = (this.height - PANEL_H) / 2;

        ResourceIndex.ensureFresh();

        if (this.tree == null) {
            this.tree = new EntryTreeView(this.font);
            this.tree.setListener(new EntryTreeView.Listener() {
                @Override
                public void onDirChosen() {
                    refreshList();
                }
            });
            this.list = new TextureEntryList(this.font);
            this.list.setSkinThumbRenderer(this::drawSkinThumb);
            this.list.setListener(new TextureEntryList.Listener() {
                @Override
                public void onActivate(ResourceLocation location) {
                    applyTexture(location);
                }

                @Override
                public void onDelete(ResourceLocation location) {
                }
            });
        }
        this.tree.setBounds(this.guiLeft + 6, this.guiTop + 20, 108, 156);
        this.list.setBounds(this.guiLeft + 120, this.guiTop + 20, 190, 156);

        this.textureBox = new EditBox(this.font, this.guiLeft + 40, this.guiTop + 183, 270, 16,
            Component.literal(""));
        this.textureBox.setMaxLength(320);
        this.textureBox.setHint(Component.literal("贴图路径，回车应用"));
        this.textureBox.setResponder(value -> this.boxMessage = "");
        this.addRenderableWidget(this.textureBox);

        if (!this.derived) {
            resetView();
            this.derived = true;
        } else {
            refreshTree();
        }

        this.addRenderableWidget(Button.builder(Component.literal("保存"), button -> saveAndReturn())
            .bounds(this.guiLeft + 284, this.guiTop + 206, 64, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("取消"), button -> cancelAndReturn())
            .bounds(this.guiLeft + 350, this.guiTop + 206, 64, 20).build());

        this.slider = new GuiSliderNop(this, 0, this.guiLeft + 318, this.guiTop + 124, 94, 20,
            this.rotation / 360f);
        this.slider.setString("旋转 " + this.rotation + "°");
        this.addRenderableWidget(this.slider);
    }

    private boolean backgroundRendered;

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.backgroundRendered) {
            return;
        }
        this.backgroundRendered = true;
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TexturePreviewLoader.drain();
        this.backgroundRendered = false;
        renderBackground(graphics, mouseX, mouseY, partialTick);
        drawTabsBack(graphics);
        drawPanel(graphics);
        drawTabsFront(graphics);
        this.tree.render(graphics, mouseX, mouseY);
        this.list.render(graphics, mouseX, mouseY);
        drawPreview(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = tabAt(mouseX, mouseY);
            if (index >= 0) {
                if (index < TAB_NAMES.length) {
                    switchTab(index);
                } else {
                    openManager();
                }
                return true;
            }
        }
        if (this.tree.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.list.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.list.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.list.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.tree.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        if (this.list.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void mouseDragged(GuiSliderNop slider) {
        this.rotation = Math.round(slider.sliderValue * 360.0f);
        slider.setString("旋转 " + this.rotation + "°");
    }

    @Override
    public void mousePressed(GuiSliderNop slider) {
    }

    @Override
    public void mouseReleased(GuiSliderNop slider) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.textureBox != null && this.textureBox.isFocused()
            && (keyCode == 257 || keyCode == 335)) {
            applyBoxValue();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void applyBoxValue() {
        if (this.textureBox == null) {
            return;
        }
        String value = this.textureBox.getValue().trim();
        if (value.isEmpty()) {
            EntryTreeView.openDefault(this.tree, MAIN_PATHS[this.tab], ROOT_PATHS[this.tab]);
            refreshList();
            return;
        }
        if (value.startsWith("http")) {
            if (this.tab == 0) {
                try {
                    this.display.setSkinUrl(value);
                    this.npc.textureLocation = null;
                    if (this.list != null) {
                        this.list.setHighlight(null);
                    }
                    this.boxMessage = "";
                } catch (Exception e) {
                    this.boxMessage = "URL无效";
                }
            } else {
                this.boxMessage = "仅皮肤支持URL";
            }
            return;
        }
        String ns = CustomSkinsPack.NAMESPACE;
        String path = value;
        int colon = value.indexOf(':');
        if (colon >= 0) {
            ns = value.substring(0, colon);
            path = value.substring(colon + 1);
        }
        if (path.isEmpty() || path.endsWith("/")) {
            String id = ns + ":" + path;
            if (EntryTreeView.exists(id)) {
                this.tree.configure(MAIN_PATHS[this.tab], id);
                refreshList();
            } else {
                this.boxMessage = "路径不存在";
            }
            return;
        }
        ResourceLocation location = ResourceIndex.rlFor(ns, path);
        if (location != null) {
            applyTexture(this.tab, location);
            String parentId = ns + ":" + TextureEntryList.parentDir(path);
            if (EntryTreeView.exists(parentId)) {
                this.tree.configure(MAIN_PATHS[this.tab], parentId);
                refreshList();
            }
            return;
        }
        if (CustomSkinsPack.NAMESPACE.equals(ns)) {
            this.boxMessage = "路径不存在";
            return;
        }
        try {
            location = ResourceLocation.fromNamespaceAndPath(ns, path);
        } catch (Exception e) {
            this.boxMessage = "路径无效";
            return;
        }
        applyTexture(this.tab, location);
    }

    private void syncBox() {
        if (this.textureBox == null) {
            return;
        }
        String value = currentString();
        if (value != null && !value.isEmpty() && !value.startsWith("http")) {
            value = ResourceIndex.toDisplay(value);
        }
        this.textureBox.setValue(value == null ? "" : value);
        this.boxMessage = "";
    }

    @Override
    public void onClose() {
        cancelAndReturn();
    }

    @Override
    public void removed() {
        if (this.closed || this.suspended) {
            return;
        }
        restoreSnapshot();
    }

    void applyTexture(int tab, ResourceLocation location) {
        if (location == null) {
            return;
        }
        String value = location.toString();
        if (tab == 0) {
            this.display.setSkinTexture(value);
        } else if (tab == 1) {
            this.display.setCapeTexture(value);
        } else {
            this.display.setOverlayTexture(value);
        }
        this.npc.textureLocation = null;
        this.npc.textureCloakLocation = null;
        this.npc.textureGlowLocation = null;
        if (this.list != null) {
            this.list.setHighlight(location);
        }
        syncBox();
    }

    private void applyTexture(ResourceLocation location) {
        applyTexture(this.tab, location);
    }

    private void switchTab(int nextTab) {
        if (nextTab == this.tab) return;
        this.tab = nextTab;
        resetView();
    }

    private void resetView() {
        String main = MAIN_PATHS[this.tab];
        String dir = main;
        String select = null;
        ResourceLocation current = currentTexture();
        String folder = null;
        if (current != null) {
            String original = ResourceIndex.toOriginal(current.getNamespace(), current.getPath());
            if (original.startsWith(ROOT_PATHS[this.tab])) {
                folder = current.getNamespace() + ":" + TextureEntryList.parentDir(original);
            }
        } else if (EntryTreeView.exists(main)) {
            folder = main;
        }
        if (folder != null && EntryTreeView.exists(folder)) {
            String parent = EntryTreeView.parentOf(folder);
            String parentPath = EntryTreeView.pathOf(parent);
            if (parentPath != null && parentPath.startsWith(ROOT_PATHS[this.tab])
                && EntryTreeView.exists(parent)) {
                dir = parent;
                select = folder;
            } else {
                dir = folder;
            }
        }
        this.tree.configure(main, dir);
        if (select != null) {
            this.tree.restoreSelection(select);
        }
        refreshList();
    }

    private void refreshTree() {
        this.tree.configure(MAIN_PATHS[this.tab], this.tree.currentDir());
        refreshList();
    }

    private void refreshList() {
        this.list.setPreviewMode(this.tab);
        this.list.setSwipeEnabled(false);
        String dir = this.tree.displayDir();
        this.list.setItems(ResourceIndex.filesIn(EntryTreeView.nsOf(dir), EntryTreeView.pathOf(dir)));
        this.list.setHighlight(currentTexture());
        syncBox();
    }

    private void openManager() {
        this.suspended = true;
        this.minecraft.setScreen(new GuiSkinsManager(this));
    }

    private String currentString() {
        if (this.tab == 0) {
            String url = this.display.getSkinUrl();
            if (this.display.skinType == 2 && url != null && !url.isEmpty()) {
                return url;
            }
            return this.display.getSkinTexture();
        }
        if (this.tab == 1) return this.display.getCapeTexture();
        return this.display.getOverlayTexture();
    }

    private ResourceLocation currentTexture() {
        return currentTextureFor(this.tab);
    }

    ResourceLocation currentTextureFor(int tab) {
        String value;
        if (tab == 0) {
            value = this.display.getSkinTexture();
        } else if (tab == 1) {
            value = this.display.getCapeTexture();
        } else {
            value = this.display.getOverlayTexture();
        }
        if (value == null || value.isEmpty() || value.startsWith("http")) return null;
        String ns = CustomSkinsPack.NAMESPACE;
        String path = value;
        int colon = value.indexOf(':');
        if (colon >= 0) {
            ns = value.substring(0, colon);
            path = value.substring(colon + 1);
        }
        ResourceLocation resolved = ResourceIndex.rlFor(ns, path);
        if (resolved != null) {
            return resolved;
        }
        try {
            return ResourceLocation.parse(value);
        } catch (Exception ignored) {
        }
        return null;
    }

    private void saveAndReturn() {
        this.closed = true;
        this.npc.textureLocation = null;
        this.npc.textureCloakLocation = null;
        this.npc.textureGlowLocation = null;
        this.parent.save();
        this.minecraft.setScreen(this.parent);
        this.parent.initGui();
    }

    private void cancelAndReturn() {
        this.closed = true;
        restoreSnapshot();
        this.minecraft.setScreen(this.parent);
        this.parent.initGui();
    }

    private void restoreSnapshot() {
        this.display.setSkinTexture(this.snapshotSkin);
        try {
            this.display.setSkinUrl(this.snapshotSkinUrl);
        } catch (Exception ignored) {
        }
        this.display.setCapeTexture(this.snapshotCape);
        this.display.setOverlayTexture(this.snapshotOverlay);
        this.display.skinType = this.snapshotSkinType;
        this.npc.textureLocation = null;
        this.npc.textureCloakLocation = null;
        this.npc.textureGlowLocation = null;
    }

    private void drawPanel(GuiGraphics graphics) {
        int x = this.guiLeft;
        int y = this.guiTop;
        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, COL_BG);
        graphics.fill(x, y, x + PANEL_W, y + 1, COL_LIGHT);
        graphics.fill(x, y, x + 1, y + PANEL_H, COL_LIGHT);
        graphics.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, COL_DARK);
        graphics.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, COL_DARK);

        graphics.drawString(this.font, "更好的材质编辑", x + 8, y + 7, COL_TEXT, false);
        String hint = null;
        if (this.tab == 0 && this.display.skinType != 0) {
            hint = this.display.skinType == 1 ? "当前: 玩家皮肤" : "当前: URL 皮肤";
            graphics.drawString(this.font, hint, x + 78, y + 7, COL_HINT, false);
        }
        int pathX = 8 + this.font.width("更好的材质编辑") + 6;
        if (hint != null) {
            pathX = Math.max(pathX, 78 + this.font.width(hint) + 6);
        }
        String path = EntryTreeView.displayPath(this.tree.displayDir());
        if (this.tree.hoveringFolder()) {
            path = path + "（双击打开）";
        }
        graphics.drawString(this.font, ellipsize(path, PANEL_W - 6 - pathX),
            x + pathX, y + 7, COL_TEXT, false);

        graphics.fill(x + 8, y + 180, x + 412, y + 181, COL_DIVIDER);
        graphics.drawString(this.font, "当前:", x + 8, y + 186, COL_TEXT, false);
        if (this.boxMessage != null && !this.boxMessage.isEmpty()) {
            graphics.drawString(this.font, ellipsize(this.boxMessage, 92),
                x + 316, y + 186, 0xFFC04040, false);
        }
    }

    private void drawTabsBack(GuiGraphics graphics) {
        int[][] rects = tabRects();
        for (int i = 0; i < rects.length; i++) {
            if (i == this.tab) {
                continue;
            }
            int tx = rects[i][0];
            int ty = rects[i][1];
            int tw = rects[i][2];
            graphics.fill(tx, ty, tx + tw, this.guiTop, COL_TAB_OFF);
            graphics.fill(tx, ty, tx + tw, ty + 1, COL_LIGHT);
            graphics.drawString(this.font, tabLabel(i), tx + 26, this.guiTop - 4,
                0xFFFFFFFF, false);
            graphics.blit(TAB_ICONS[i], tx + 8, this.guiTop - 8, 16, 16,
                0.0F, 0.0F, 16, 16, 16, 16);
        }
    }

    private void drawTabsFront(GuiGraphics graphics) {
        int[][] rects = tabRects();
        int i = this.tab;
        int tx = rects[i][0];
        int ty = rects[i][1];
        int tw = rects[i][2];
        graphics.fill(tx, ty, tx + tw, this.guiTop + 1, COL_BG);
        graphics.fill(tx, ty, tx + 1, this.guiTop + 1, COL_LIGHT);
        graphics.fill(tx, ty, tx + tw, ty + 1, COL_LIGHT);
        graphics.fill(tx + tw - 1, ty, tx + tw, this.guiTop + 1, COL_DARK);
        graphics.drawString(this.font, tabLabel(i), tx + 26, ty + 4, COL_TEXT, false);
        graphics.blit(TAB_ICONS[i], tx + 8, ty + 1, 16, 16,
            0.0F, 0.0F, 16, 16, 16, 16);
    }

    private String tabLabel(int i) {
        return i < TAB_NAMES.length ? TAB_NAMES[i] : MANAGER_TAB;
    }

    private int[][] tabRects() {
        int top = Math.max(0, this.guiTop - TAB_H);
        int height = this.guiTop + 1 - top;
        int[][] rects = new int[TAB_NAMES.length + 1][4];
        int tx = this.guiLeft + 6;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tw = tabWidth(TAB_NAMES[i]);
            rects[i] = new int[]{tx, top, tw, height};
            tx += tw + 2;
        }
        int sw = tabWidth(MANAGER_TAB);
        rects[TAB_NAMES.length] = new int[]{this.guiLeft + PANEL_W - 6 - sw, top, sw, height};
        return rects;
    }

    private int tabWidth(String label) {
        return Math.max(36, this.font.width(label) + 34);
    }

    private int tabAt(double mouseX, double mouseY) {
        int[][] rects = tabRects();
        for (int i = 0; i < rects.length; i++) {
            if (mouseX >= rects[i][0] && mouseX < rects[i][0] + rects[i][2]
                && mouseY >= rects[i][1] && mouseY < rects[i][1] + rects[i][3]) {
                return i;
            }
        }
        return -1;
    }

    private void drawPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(this.font, "NPC 预览", this.guiLeft + 365,
            this.guiTop + 22, COL_TEXT);
        drawNpc(graphics, this.guiLeft + 365, this.guiTop + 116, 1.3f, this.rotation,
            mouseX, mouseY);
    }

    private void drawNpc(GuiGraphics graphics, int x, int y, float zoom, int angle, int mouseX, int mouseY) {
        CustomGuiEntityDisplay.drawEntity(graphics, this.npc, x, y, zoom, angle, 0, 0,
            0f, 0f, false);
    }

    public void drawSkinThumb(GuiGraphics graphics, ResourceLocation location,
        int x, int y, int size, boolean back) {
        if (location == null) {
            return;
        }
        String previousSkin = this.display.getSkinTexture();
        byte previousType = this.display.skinType;
        try {
            this.display.setSkinTexture(location.toString());
            this.npc.textureLocation = null;
            graphics.enableScissor(x, y, x + size, y + size);
            CustomGuiEntityDisplay.drawEntity(graphics, this.npc, x + size / 2, y + size,
                0.40f, back ? 180 : 0, 0, 0, 0f, 0f, false);
            graphics.disableScissor();
        } catch (Exception ignored) {
        } finally {
            this.display.setSkinTexture(previousSkin);
            this.display.skinType = previousType;
            this.npc.textureLocation = null;
        }
    }

    private String ellipsize(String text, int maxWidth) {
        return TextureEntryList.ellipsize(this.font, text, maxWidth);
    }
}
