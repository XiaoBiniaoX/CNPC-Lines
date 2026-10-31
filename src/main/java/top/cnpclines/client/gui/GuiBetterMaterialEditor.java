package top.cnpclines.client.gui;

import java.io.IOException;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.client.gui.util.GuiNpcSlider;
import noppes.npcs.client.gui.util.ISliderListener;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPC64x32;
import noppes.npcs.client.model.ModelPlayerAlt;
import noppes.npcs.client.renderer.RenderCustomNpc;
import noppes.npcs.client.layer.LayerPreRender;
import noppes.npcs.entity.data.DataDisplay;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import top.cnpclines.client.CustomSkinsPack;

public class GuiBetterMaterialEditor extends GuiScreen implements ISliderListener {

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

    private static final int BTN_SAVE = 100;
    private static final int BTN_CANCEL = 101;
    private static final int BTN_MANAGER = 102;
    private static final int SLIDER_ROTATION = 0;

    private static final String[] TAB_KEYS = {
        "cnpclines.tab.material", "cnpclines.tab.cloak", "cnpclines.tab.overlays",
    };
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
        new ResourceLocation("cnpclines", "textures/overlay/message/icons/skins.png"),
        new ResourceLocation("cnpclines", "textures/overlay/message/icons/cloak.png"),
        new ResourceLocation("cnpclines", "textures/overlay/message/icons/overlays.png"),
        new ResourceLocation("cnpclines", "textures/overlay/message/icons/management.png"),
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
    private GuiNpcSlider slider;
    private GuiTextField textureBox;
    private GuiTextField searchBox;
    private SearchResultView results;
    private String boxMessage = "";
    private ModernSkinPreview modernSkinPreview;

    public GuiBetterMaterialEditor(GuiNpcDisplay parent) {
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
    public void initGui() {
        this.suspended = false;
        this.guiLeft = (this.width - PANEL_W) / 2;
        this.guiTop = (this.height - PANEL_H) / 2;

        ResourceIndex.ensureFresh();

        if (this.tree == null) {
            this.tree = new EntryTreeView(this.fontRenderer);
            this.tree.setListener(new EntryTreeView.Listener() {
                @Override
                public void onDirChosen() {
                    refreshList();
                }
            });
            this.list = new TextureEntryList(this.fontRenderer);
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
            this.results = new SearchResultView(this.fontRenderer);
            this.results.setListener(this::locate);
            this.results.setSkinThumbRenderer(this::drawSkinThumb);
        }
        this.searchBox = new GuiTextField(3, this.fontRenderer, this.guiLeft + 6, this.guiTop + 20, 304, 16);
        this.searchBox.setMaxStringLength(64);
        this.searchBox.setText("");
        this.searchBox.setFocused(false);
        this.tree.setBounds(this.guiLeft + 6, this.guiTop + 38, 108, 138);
        this.list.setBounds(this.guiLeft + 120, this.guiTop + 38, 190, 138);
        this.results.setBounds(this.guiLeft + 6, this.guiTop + 38, 304, 138);

        this.textureBox = new GuiTextField(0, this.fontRenderer,
            this.guiLeft + 40, this.guiTop + 183, 270, 16);
        this.textureBox.setMaxStringLength(320);

        if (!this.derived) {
            resetView();
            this.derived = true;
        } else {
            refreshTree();
        }
        refreshSearch();

        this.buttonList.add(new GuiButton(BTN_SAVE, this.guiLeft + 284, this.guiTop + 206, 64, 20,
            I18n.format("cnpclines.editor.save")));
        this.buttonList.add(new GuiButton(BTN_CANCEL, this.guiLeft + 350, this.guiTop + 206, 64, 20,
            I18n.format("cnpclines.editor.cancel")));
        this.buttonList.add(new GuiButton(BTN_MANAGER, this.guiLeft + 8, this.guiTop + 206, 94, 20,
            I18n.format("cnpclines.tab.manager")));

        this.slider = new GuiNpcSlider(this, SLIDER_ROTATION, this.guiLeft + 318, this.guiTop + 124, 94, 20,
            this.rotation / 360f);
        this.slider.setString(I18n.format("cnpclines.editor.rotation", this.rotation));
        this.buttonList.add(this.slider);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TexturePreviewLoader.drain();
        this.drawDefaultBackground();
        drawTabsBack();
        drawPanel();
        drawTabsFront();
        if (isSearchActive()) {
            this.results.render(mouseX, mouseY);
        } else {
            this.tree.render(mouseX, mouseY);
            this.list.render(mouseX, mouseY);
        }
        drawPreview(mouseX, mouseY);
        drawTextureBox();
        drawSearchBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSearchBox() {
        if (this.searchBox == null) {
            return;
        }
        this.searchBox.drawTextBox();
        if (this.searchBox.getText().isEmpty()) {
            this.fontRenderer.drawString(I18n.format("cnpclines.search.hint"),
                this.searchBox.x + 3, this.searchBox.y + 4, 0xFF909090);
        }
    }

    private void drawTextureBox() {
        if (this.textureBox == null) {
            return;
        }
        this.textureBox.drawTextBox();
        if (this.textureBox.getText().isEmpty()) {
            this.fontRenderer.drawString(I18n.format("cnpclines.editor.path_hint"),
                this.textureBox.x + 4, this.textureBox.y + 4, 0xFFA0A0A0);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int index = tabAt(mouseX, mouseY);
            if (index >= 0) {
                switchTab(index);
                return;
            }
        }
        if (this.searchBox != null) {
            boolean inSearch = this.searchBox.mouseClicked(mouseX, mouseY, mouseButton);
            if (inSearch) {
                if (this.textureBox != null) {
                    this.textureBox.setFocused(false);
                }
                return;
            }
            if (mouseButton == 0) {
                this.searchBox.setFocused(false);
            }
        }
        if (isSearchActive()) {
            if (this.results.mouseClicked(mouseX, mouseY, mouseButton)) return;
        } else {
            if (this.tree.mouseClicked(mouseX, mouseY, mouseButton)) return;
            if (this.list.mouseClicked(mouseX, mouseY, mouseButton)) return;
        }
        if (this.textureBox != null) {
            boolean inBox = this.textureBox.mouseClicked(mouseX, mouseY, mouseButton);
            if (!inBox && mouseButton == 0) {
                this.textureBox.setFocused(false);
            }
            if (inBox) return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedButton, long timeSinceLastClick) {
        if (this.list.mouseDragged(mouseX, mouseY, clickedButton, 0, 0)) return;
        super.mouseClickMove(mouseX, mouseY, clickedButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (this.list.mouseReleased(mouseX, mouseY, state)) return;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            double delta = wheel > 0 ? 1 : -1;
            if (isSearchActive()) {
                if (this.results.mouseScrolled(mouseX, mouseY, delta)) return;
            } else {
                if (this.tree.mouseScrolled(mouseX, mouseY, delta)) return;
                if (this.list.mouseScrolled(mouseX, mouseY, delta)) return;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_SAVE) {
            saveAndReturn();
            return;
        }
        if (button.id == BTN_CANCEL) {
            cancelAndReturn();
            return;
        }
        if (button.id == BTN_MANAGER) {
            openManager();
            return;
        }
        super.actionPerformed(button);
    }

    @Override
    public void mouseDragged(GuiNpcSlider slider) {
        this.rotation = Math.round(slider.sliderValue * 360.0f);
        slider.setString(I18n.format("cnpclines.editor.rotation", this.rotation));
    }

    @Override
    public void mousePressed(GuiNpcSlider slider) {
    }

    @Override
    public void mouseReleased(GuiNpcSlider slider) {
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                SearchResultView.Row row = this.results.first();
                if (row != null) {
                    locate(row.location, row.dirId);
                }
                return;
            }
            if (keyCode == 1) {
                if (isSearchActive()) {
                    clearSearch();
                    return;
                }
                cancelAndReturn();
                return;
            }
            this.searchBox.textboxKeyTyped(typedChar, keyCode);
            refreshSearch();
            return;
        }
        if (keyCode == 1) {
            cancelAndReturn();
            return;
        }
        if (this.textureBox != null && this.textureBox.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                applyBoxValue();
                return;
            }
            String before = this.textureBox.getText();
            if (this.textureBox.textboxKeyTyped(typedChar, keyCode)) {
                if (!before.equals(this.textureBox.getText())) {
                    this.boxMessage = "";
                }
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void applyBoxValue() {
        if (this.textureBox == null) {
            return;
        }
        String value = this.textureBox.getText().trim();
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
                    this.boxMessage = I18n.format("cnpclines.editor.box.url_invalid");
                }
            } else {
                this.boxMessage = I18n.format("cnpclines.editor.box.url_only");
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
                this.boxMessage = I18n.format("cnpclines.editor.box.path_missing");
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
            this.boxMessage = I18n.format("cnpclines.editor.box.path_missing");
            return;
        }
        if (!ResourceIndex.isValidNamespace(ns) || !ResourceIndex.isValidPath(path)) {
            this.boxMessage = I18n.format("cnpclines.editor.box.path_invalid");
            return;
        }
        location = new ResourceLocation(ns, path);
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
        this.textureBox.setText(value == null ? "" : value);
        this.boxMessage = "";
    }

    @Override
    public void onGuiClosed() {
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
        refreshSearch();
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

    private boolean isSearchActive() {
        return this.searchBox != null && !this.searchBox.getText().trim().isEmpty()
            && this.results != null;
    }

    private void refreshSearch() {
        if (this.results == null) {
            return;
        }
        String q = this.searchBox == null ? "" : this.searchBox.getText().trim();
        String scope = searchScopeId();
        this.results.setScope(EntryTreeView.displayPath(scope));
        this.results.setPreviewMode(this.tab);
        this.results.setHighlight(currentTexture());
        this.results.setResults(q.isEmpty()
            ? java.util.Collections.<SearchResultView.Row>emptyList()
            : SkinSearch.query(q, scope));
    }

    private String searchScopeId() {
        String dir = this.tree.displayDir();
        String parent = EntryTreeView.parentOf(dir);
        if (parent == null) {
            return dir;
        }
        String parentPath = EntryTreeView.pathOf(parent);
        if (parentPath == null || parentPath.isEmpty()) {
            return dir;
        }
        return parent;
    }

    private void clearSearch() {
        this.searchBox.setText("");
        this.searchBox.setFocused(false);
        refreshSearch();
    }

    private void locate(ResourceLocation location, String dirId) {
        int nextTab = SkinSearch.tabForPath(dirId);
        if (nextTab >= 0) {
            this.tab = nextTab;
        }
        clearSearch();
        this.tree.configure(MAIN_PATHS[this.tab], dirId);
        this.tree.restoreSelection(dirId);
        refreshList();
        this.list.setSelected(location);
        this.boxMessage = I18n.format("cnpclines.editor.box.located", EntryTreeView.displayPath(dirId));
    }

    private void openManager() {
        this.suspended = true;
        this.mc.displayGuiScreen(new GuiSkinsManager(this));
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
        if (!ResourceIndex.isValidNamespace(ns) || !ResourceIndex.isValidPath(path)) {
            return null;
        }
        return new ResourceLocation(ns, path);
    }

    private void saveAndReturn() {
        this.closed = true;
        this.npc.textureLocation = null;
        this.npc.textureCloakLocation = null;
        this.npc.textureGlowLocation = null;
        this.parent.save();
        this.mc.displayGuiScreen(this.parent);
    }

    private void cancelAndReturn() {
        this.closed = true;
        restoreSnapshot();
        this.mc.displayGuiScreen(this.parent);
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

    private void drawPanel() {
        int x = this.guiLeft;
        int y = this.guiTop;
        Gui.drawRect(x, y, x + PANEL_W, y + PANEL_H, COL_BG);
        Gui.drawRect(x, y, x + PANEL_W, y + 1, COL_LIGHT);
        Gui.drawRect(x, y, x + 1, y + PANEL_H, COL_LIGHT);
        Gui.drawRect(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, COL_DARK);
        Gui.drawRect(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, COL_DARK);

        String title = I18n.format("cnpclines.editor.title");
        this.fontRenderer.drawString(title, x + 8, y + 7, COL_TEXT);
        String hint = null;
        if (this.tab == 0 && this.display.skinType != 0) {
            hint = this.display.skinType == 1
                ? I18n.format("cnpclines.editor.hint_player")
                : I18n.format("cnpclines.editor.hint_url");
            this.fontRenderer.drawString(hint, x + 78, y + 7, COL_HINT);
        }
        int pathX = 8 + this.fontRenderer.getStringWidth(title) + 6;
        if (hint != null) {
            pathX = Math.max(pathX, 78 + this.fontRenderer.getStringWidth(hint) + 6);
        }
        String path = EntryTreeView.displayPath(this.tree.displayDir());
        if (this.tree.hoveringFolder()) {
            path = path + I18n.format("cnpclines.editor.dblclick");
        }
        this.fontRenderer.drawString(ellipsize(path, PANEL_W - 6 - pathX),
            x + pathX, y + 7, COL_TEXT);

        Gui.drawRect(x + 8, y + 180, x + 412, y + 181, COL_DIVIDER);
        this.fontRenderer.drawString(I18n.format("cnpclines.editor.current"), x + 8, y + 186, COL_TEXT);
        if (this.boxMessage != null && !this.boxMessage.isEmpty()) {
            this.fontRenderer.drawString(ellipsize(this.boxMessage, 92),
                x + 316, y + 186, 0xFFC04040);
        }
    }

    private void drawTabsBack() {
        int[][] rects = tabRects();
        for (int i = 0; i < rects.length; i++) {
            if (i == this.tab) {
                continue;
            }
            int tx = rects[i][0];
            int ty = rects[i][1];
            int tw = rects[i][2];
            Gui.drawRect(tx, ty, tx + tw, this.guiTop, COL_TAB_OFF);
            Gui.drawRect(tx, ty, tx + tw, ty + 1, COL_LIGHT);
            drawCentered(I18n.format(TAB_KEYS[i]), tx + 26, this.guiTop - 4, 0xFFFFFFFF);
            drawIcon(TAB_ICONS[i], tx + 8, this.guiTop - 8);
        }
    }

    private void drawTabsFront() {
        int[][] rects = tabRects();
        int i = this.tab;
        int tx = rects[i][0];
        int ty = rects[i][1];
        int tw = rects[i][2];
        Gui.drawRect(tx, ty, tx + tw, this.guiTop + 1, COL_BG);
        Gui.drawRect(tx, ty, tx + 1, this.guiTop + 1, COL_LIGHT);
        Gui.drawRect(tx, ty, tx + tw, ty + 1, COL_LIGHT);
        Gui.drawRect(tx + tw - 1, ty, tx + tw, this.guiTop + 1, COL_DARK);
        this.fontRenderer.drawString(I18n.format(TAB_KEYS[i]), tx + 26, ty + 4, COL_TEXT);
        drawIcon(TAB_ICONS[i], tx + 8, ty + 1);
    }

    private void drawCentered(String text, int centerX, int y, int color) {
        this.fontRenderer.drawString(text, centerX - this.fontRenderer.getStringWidth(text) / 2, y, color);
    }

    private void drawIcon(ResourceLocation rl, int x, int y) {
        this.mc.getTextureManager().bindTexture(rl);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    private int[][] tabRects() {
        int top = Math.max(0, this.guiTop - TAB_H);
        int height = this.guiTop + 1 - top;
        int[][] rects = new int[TAB_KEYS.length][4];
        int tx = this.guiLeft + 6;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int tw = tabWidth(I18n.format(TAB_KEYS[i]));
            rects[i] = new int[]{tx, top, tw, height};
            tx += tw + 2;
        }
        return rects;
    }

    private int tabWidth(String label) {
        return Math.max(36, this.fontRenderer.getStringWidth(label) + 34);
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

    private void drawPreview(int mouseX, int mouseY) {
        drawCentered(I18n.format("cnpclines.editor.preview"), this.guiLeft + 365, this.guiTop + 22, COL_TEXT);
        drawNpcPreview(this.guiLeft + 365, this.guiTop + 116, 1.0f, this.rotation);
    }

    void drawNpcPreview(int absX, int absY, float zoom, int angle) {
        float scale = this.npc.height > 2.4f ? 2.0f / this.npc.height : 1.0f;
        this.parent.mouseX = absX;
        this.parent.mouseY = absY - (int) (50.0f * scale * zoom);
        int relX = absX - this.parent.guiLeft;
        int relY = absY - this.parent.guiTop;
        EntityNPCInterface modelNpc = this.npc;
        if (this.npc instanceof EntityCustomNpc) {
            net.minecraft.entity.EntityLivingBase modelEntity = ((EntityCustomNpc) this.npc).modelData.getEntity(this.npc);
            if (modelEntity != null) {
                modelNpc = modelEntity instanceof EntityNPCInterface ? (EntityNPCInterface) modelEntity : null;
            }
        }
        TexturePreviewLoader.Preview skin = modelNpc instanceof EntityNPC64x32
            && this.display.skinType == 0 ? TexturePreviewLoader.peek(currentTextureFor(0)) : null;
        if (skin == null || skin.failed() || skin.width() < 64 || skin.width() != skin.height()) {
            this.parent.drawNpc(this.npc, relX, relY, zoom, angle);
            return;
        }
        RenderManager manager = this.mc.getRenderManager();
        Class<? extends net.minecraft.entity.Entity> modelClass = modelNpc.getClass();
        Render<? extends net.minecraft.entity.Entity> original = manager.entityRenderMap.get(modelClass);
        if (this.modernSkinPreview == null) {
            this.modernSkinPreview = new ModernSkinPreview();
        }
        this.modernSkinPreview.prepare((EntityCustomNpc) modelNpc);
        // The legacy biped has no jacket/sleeve/pants geometry. Borrow a complete model only during preview.
        manager.entityRenderMap.put(modelClass, this.modernSkinPreview);
        try {
            this.parent.drawNpc(this.npc, relX, relY, zoom, angle);
        } finally {
            if (original == null) {
                manager.entityRenderMap.remove(modelClass);
            } else {
                manager.entityRenderMap.put(modelClass, original);
            }
        }
    }

    private static final class ModernSkinPreview extends RenderCustomNpc<EntityCustomNpc> {
        ModernSkinPreview() {
            super(new ModelPlayerAlt(0.0F, false));
        }

        void prepare(EntityCustomNpc npc) {
            // CNPC's nested model path calls renderModel/renderLayers without doRender's layer initialization.
            for (LayerRenderer layer : this.layerRenderers) {
                if (layer instanceof LayerPreRender) {
                    ((LayerPreRender) layer).preRender(npc);
                }
            }
        }
    }

    public void drawSkinThumb(ResourceLocation location, int x, int y, int size, boolean back) {
        if (location == null) {
            return;
        }
        String previousSkin = this.display.getSkinTexture();
        byte previousType = this.display.skinType;
        try {
            this.display.setSkinTexture(location.toString());
            this.npc.textureLocation = null;
            enableScissor(x, y, x + size, y + size);
            drawNpcPreview(x + size / 2, y + size, 0.40f, back ? 180 : 0);
        } catch (Exception ignored) {
        } finally {
            disableScissor();
            this.display.setSkinTexture(previousSkin);
            this.display.skinType = previousType;
            this.npc.textureLocation = null;
        }
    }

    private void enableScissor(int x1, int y1, int x2, int y2) {
        ScaledResolution sr = new ScaledResolution(this.mc);
        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x1 * scale, this.mc.displayHeight - y2 * scale,
            (x2 - x1) * scale, (y2 - y1) * scale);
    }

    private void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private String ellipsize(String text, int maxWidth) {
        return TextureEntryList.ellipsize(this.fontRenderer, text, maxWidth);
    }
}
