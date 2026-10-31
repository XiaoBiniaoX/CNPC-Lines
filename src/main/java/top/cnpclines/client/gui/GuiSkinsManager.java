package top.cnpclines.client.gui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import top.cnpclines.client.CustomSkinsPack;

public class GuiSkinsManager extends GuiScreen {

    private static final Logger LOGGER = LogManager.getLogger("cnpclines");

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

    private static final String[] TAB_KEYS = {
        "cnpclines.tab.skins", "cnpclines.tab.cloak", "cnpclines.tab.overlays",
    };
    private static final ResourceLocation[] TAB_ICONS = {
        new ResourceLocation("cnpclines", "textures/overlay/message/icons/skins.png"),
        new ResourceLocation("cnpclines", "textures/overlay/message/icons/cloak.png"),
        new ResourceLocation("cnpclines", "textures/overlay/message/icons/overlays.png"),
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

    private static final int MODE_NONE = 0;
    private static final int MODE_CREATE = 1;
    private static final int MODE_RENAME = 2;

    private static final int FOCUS_NONE = 0;
    private static final int FOCUS_TREE = 1;
    private static final int FOCUS_LIST = 2;

    private static final int BTN_CREATE = 110;
    private static final int BTN_RENAME = 111;
    private static final int BTN_DELETE = 112;
    private static final int BTN_OK = 113;
    private static final int BTN_BACK = 114;
    private static final int BTN_IMPORT = 115;

    private final GuiBetterMaterialEditor parent;

    private int guiLeft;
    private int guiTop;
    private int tab;

    private EntryTreeView tree;
    private TextureEntryList list;
    private GuiTextField nameBox;
    private GuiTextField searchBox;
    private SearchResultView results;
    private GuiButton deleteButton;
    private String nameHint = "";

    private int mode = MODE_NONE;
    private String targetId;
    private boolean armedDelete;
    private long armedAt;
    private int deleteFocus = FOCUS_NONE;
    private ResourceLocation pendingFile;
    private String pendingDir;
    private volatile String status = "";
    private volatile List<Path> pendingImportFiles;
    private volatile boolean importDialogOpen;

    public GuiSkinsManager(GuiBetterMaterialEditor parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.guiLeft = (this.width - PANEL_W) / 2;
        this.guiTop = (this.height - PANEL_H) / 2;

        ResourceIndex.ensureFresh();

        if (this.tree == null) {
            this.tree = new EntryTreeView(this.fontRenderer);
            this.tree.setListener(new EntryTreeView.Listener() {
                @Override
                public void onDirChosen() {
                    deleteFocus = FOCUS_TREE;
                    refreshList();
                    disarmDelete();
                }
            });
            this.list = new TextureEntryList(this.fontRenderer);
            this.list.setSkinThumbRenderer(parent::drawSkinThumb);
            this.list.setListener(new TextureEntryList.Listener() {
                @Override
                public void onActivate(ResourceLocation location) {
                    parent.applyTexture(GuiSkinsManager.this.tab, location);
                }

                @Override
                public void onSelect(ResourceLocation location) {
                    deleteFocus = FOCUS_LIST;
                    disarmDelete();
                }

                @Override
                public void onDelete(ResourceLocation location) {
                    deleteFile(location);
                }
            });
            EntryTreeView.openDefault(this.tree, MAIN_PATHS[this.tab], ROOT_PATHS[this.tab]);
            this.results = new SearchResultView(this.fontRenderer);
            this.results.setListener(this::locate);
            this.results.setSkinThumbRenderer(parent::drawSkinThumb);
        }
        this.searchBox = new GuiTextField(2, this.fontRenderer, this.guiLeft + 6, this.guiTop + 20, 304, 16);
        this.searchBox.setMaxStringLength(64);
        this.searchBox.setText("");
        this.searchBox.setFocused(false);
        this.tree.setBounds(this.guiLeft + 6, this.guiTop + 38, 108, 138);
        this.list.setBounds(this.guiLeft + 120, this.guiTop + 38, 190, 138);
        this.results.setBounds(this.guiLeft + 6, this.guiTop + 38, 304, 138);
        refreshList();
        refreshSearch();

        this.buttonList.add(new GuiButton(BTN_CREATE, this.guiLeft + 316, this.guiTop + 20, 98, 18,
            I18n.format("cnpclines.manager.new_folder")));
        this.buttonList.add(new GuiButton(BTN_RENAME, this.guiLeft + 316, this.guiTop + 41, 98, 18,
            I18n.format("cnpclines.manager.rename")));
        this.deleteButton = new GuiButton(BTN_DELETE, this.guiLeft + 316, this.guiTop + 62, 98, 18,
            I18n.format("cnpclines.manager.delete"));
        this.buttonList.add(this.deleteButton);

        this.nameBox = new GuiTextField(0, this.fontRenderer, this.guiLeft + 316, this.guiTop + 84, 66, 18);
        this.nameBox.setMaxStringLength(32);

        this.buttonList.add(new GuiButton(BTN_OK, this.guiLeft + 384, this.guiTop + 84, 30, 18,
            I18n.format("cnpclines.manager.ok")));
        this.buttonList.add(new GuiButton(BTN_IMPORT, this.guiLeft + 316, this.guiTop + 150, 98, 18,
            I18n.format("cnpclines.manager.import")));
        this.buttonList.add(new GuiButton(BTN_BACK, this.guiLeft + 350, this.guiTop + 206, 64, 20,
            I18n.format("cnpclines.manager.back")));
    }

    private void refreshList() {
        this.list.setPreviewMode(this.tab);
        this.list.setSwipeEnabled(true);
        String dir = this.tree.displayDir();
        this.list.setItems(ResourceIndex.filesIn(EntryTreeView.nsOf(dir), EntryTreeView.pathOf(dir)));
        this.list.setHighlight(this.parent.currentTextureFor(this.tab));
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
        this.results.setHighlight(this.parent.currentTextureFor(this.tab));
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
        if (nextTab >= 0 && nextTab != this.tab) {
            this.tab = nextTab;
            this.mode = MODE_NONE;
            this.targetId = null;
            disarmDelete();
        }
        clearSearch();
        this.tree.configure(MAIN_PATHS[this.tab], dirId);
        this.tree.restoreSelection(dirId);
        refreshList();
        this.list.setSelected(location);
        this.status = I18n.format("cnpclines.status.located", EntryTreeView.displayPath(dirId));
        playClick();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TexturePreviewLoader.drain();
        List<Path> importFiles = this.pendingImportFiles;
        if (importFiles != null) {
            this.pendingImportFiles = null;
            if (!importFiles.isEmpty()) {
                importSelectedFiles(importFiles);
            }
        }
        if (this.armedDelete && System.currentTimeMillis() - this.armedAt > 4000) {
            disarmDelete();
        }
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
        drawControls();
        drawNameBox();
        drawSearchBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawTooltips(mouseX, mouseY);
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

    private void drawNameBox() {
        if (this.nameBox == null) {
            return;
        }
        this.nameBox.drawTextBox();
        if (this.nameBox.getText().isEmpty() && !this.nameHint.isEmpty()) {
            this.fontRenderer.drawString(this.nameHint,
                this.nameBox.x + 3, this.nameBox.y + 5, 0xFF909090);
        }
    }

    private void drawTooltips(int mouseX, int mouseY) {
        String tip = null;
        if (inside(this.deleteButton, mouseX, mouseY)) {
            tip = I18n.format("cnpclines.tip.delete");
        } else if (mouseOverButton(BTN_CREATE, mouseX, mouseY)) {
            tip = I18n.format("cnpclines.tip.create");
        } else if (mouseOverButton(BTN_RENAME, mouseX, mouseY)) {
            tip = I18n.format("cnpclines.tip.rename");
        } else if (mouseOverButton(BTN_IMPORT, mouseX, mouseY)) {
            tip = I18n.format("cnpclines.tip.import");
        }
        if (tip != null) {
            this.drawHoveringText(Arrays.asList(tip.split("\\n")), mouseX, mouseY);
        }
    }

    private boolean mouseOverButton(int id, int mouseX, int mouseY) {
        for (GuiButton button : this.buttonList) {
            if (button.id == id) {
                return inside(button, mouseX, mouseY);
            }
        }
        return false;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int index = tabAt(mouseX, mouseY);
            if (index >= 0) {
                switchTab(index);
                return;
            }
            if (this.armedDelete && !inside(this.deleteButton, mouseX, mouseY)
                && !inside(this.nameBox, mouseX, mouseY)) {
                disarmDelete();
            }
        }
        if (this.searchBox != null) {
            boolean inSearch = this.searchBox.mouseClicked(mouseX, mouseY, mouseButton);
            if (inSearch) {
                if (this.nameBox != null) {
                    this.nameBox.setFocused(false);
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
        if (this.nameBox != null) {
            boolean inBox = this.nameBox.mouseClicked(mouseX, mouseY, mouseButton);
            if (inBox) return;
            if (mouseButton == 0) {
                this.nameBox.setFocused(false);
            }
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
        switch (button.id) {
            case BTN_CREATE:
                beginCreate();
                return;
            case BTN_RENAME:
                beginRename();
                return;
            case BTN_DELETE:
                pressDelete();
                return;
            case BTN_OK:
                confirmName();
                return;
            case BTN_IMPORT:
                openImportDialog();
                return;
            case BTN_BACK:
                close();
                return;
            default:
                super.actionPerformed(button);
        }
    }

    private void importFromClipboard() {
        String id = this.tree.displayDir();
        String dir = EntryTreeView.pathOf(id);
        if (!CustomSkinsPack.NAMESPACE.equals(EntryTreeView.nsOf(id))
            || dir == null || dir.isEmpty()) {
            this.status = I18n.format("cnpclines.status.other_pack_import");
            return;
        }
        String clipboard = GuiScreen.getClipboardString();
        if (clipboard == null || clipboard.trim().isEmpty()) {
            this.status = I18n.format("cnpclines.status.clipboard_empty");
            return;
        }
        List<Path> paths = new ArrayList<>();
        for (String line : clipboard.split("\r?\n")) {
            String value = line.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 2) {
                value = value.substring(1, value.length() - 1);
            }
            if (value.startsWith("file:/")) {
                try {
                    value = java.nio.file.Paths.get(java.net.URI.create(value)).toString();
                } catch (Exception ignored) {
                }
            }
            paths.add(java.nio.file.Paths.get(value));
        }
        importPngTo(id, dir, paths);
    }

    private void openImportDialog() {
        if (this.importDialogOpen) {
            return;
        }
        this.importDialogOpen = true;
        this.status = I18n.format("cnpclines.status.opening_dialog");
        LOGGER.info("import dialog: opening JFileChooser");
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.JFileChooser chooser = new javax.swing.JFileChooser() {
                    @Override
                    protected javax.swing.JDialog createDialog(java.awt.Component parent)
                        throws java.awt.HeadlessException {
                        javax.swing.JDialog dialog = super.createDialog(parent);
                        dialog.setAlwaysOnTop(true);
                        return dialog;
                    }
                };
                chooser.setDialogTitle(I18n.format("cnpclines.import.dialog_title"));
                chooser.setMultiSelectionEnabled(true);
                chooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    I18n.format("cnpclines.import.filter"), "png"));
                int result = chooser.showOpenDialog(null);
                LOGGER.info("import dialog: closed with {}", result);
                if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                    List<Path> paths = new ArrayList<>();
                    for (java.io.File file : chooser.getSelectedFiles()) {
                        paths.add(file.toPath());
                    }
                    if (!paths.isEmpty()) {
                        this.pendingImportFiles = paths;
                    }
                } else {
                    this.status = I18n.format("cnpclines.status.import_cancelled");
                }
            } catch (Throwable t) {
                LOGGER.error("import dialog failed", t);
                this.status = I18n.format("cnpclines.status.dialog_failed", String.valueOf(t));
            } finally {
                this.importDialogOpen = false;
            }
        });
    }

    private void importSelectedFiles(List<Path> paths) {
        String id = this.tree.displayDir();
        String dir = EntryTreeView.pathOf(id);
        if (!CustomSkinsPack.NAMESPACE.equals(EntryTreeView.nsOf(id))
            || dir == null || dir.isEmpty()) {
            this.status = I18n.format("cnpclines.status.other_pack_import");
            return;
        }
        importPngTo(id, dir, paths);
    }

    private void importPngTo(String id, String dir, List<Path> paths) {
        try {
            CustomSkinsPack.ImportResult result = CustomSkinsPack.importPng(dir, paths);
            if (result.added() <= 0 && result.skipped() <= 0) {
                this.status = I18n.format("cnpclines.status.no_files");
                return;
            }
            StringBuilder message = new StringBuilder();
            if (result.added() > 0) {
                message.append(I18n.format("cnpclines.status.imported", result.added())).append(' ');
                List<String> names = result.names();
                for (int i = 0; i < names.size() && i < 2; i++) {
                    if (i > 0) {
                        message.append(I18n.format("cnpclines.status.name_sep"));
                    }
                    message.append(names.get(i));
                }
                if (names.size() > 2) {
                    message.append(I18n.format("cnpclines.status.import_more", names.size()));
                }
                ResourceIndex.refresh();
                this.tree.configure(MAIN_PATHS[this.tab], this.tree.currentDir());
                this.tree.restoreSelection(id);
                refreshList();
                playClick();
            }
            if (result.skipped() > 0) {
                if (message.length() > 0) {
                    message.append(I18n.format("cnpclines.status.sentence_sep"));
                }
                message.append(I18n.format("cnpclines.status.skipped", result.skipped()));
            }
            this.status = message.toString();
        } catch (Exception e) {
            this.status = I18n.format("cnpclines.status.import_failed", String.valueOf(e.getMessage()));
        }
    }

    private void close() {
        this.mc.displayGuiScreen(this.parent);
    }

    private void switchTab(int nextTab) {
        if (nextTab == this.tab) {
            return;
        }
        this.tab = nextTab;
        this.mode = MODE_NONE;
        this.targetId = null;
        this.deleteFocus = FOCUS_NONE;
        disarmDelete();
        EntryTreeView.openDefault(this.tree, MAIN_PATHS[this.tab], ROOT_PATHS[this.tab]);
        refreshList();
        refreshSearch();
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
                close();
                return;
            }
            this.searchBox.textboxKeyTyped(typedChar, keyCode);
            refreshSearch();
            return;
        }
        if (keyCode == 1) {
            close();
            return;
        }
        if (this.nameBox != null && this.nameBox.isFocused()) {
            if (this.mode != MODE_NONE
                && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)) {
                confirmName();
                return;
            }
            this.nameBox.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        if (GuiScreen.isKeyComboCtrlV(typedChar)) {
            importFromClipboard();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private boolean ensureOwnTarget() {
        String id = this.tree.displayDir();
        if (!CustomSkinsPack.NAMESPACE.equals(EntryTreeView.nsOf(id))) {
            this.status = I18n.format("cnpclines.status.other_pack_op");
            return false;
        }
        String path = EntryTreeView.pathOf(id);
        if (path == null || path.isEmpty()) {
            this.status = I18n.format("cnpclines.status.ns_root_op");
            return false;
        }
        return true;
    }

    private void beginCreate() {
        if (!ensureOwnTarget()) {
            return;
        }
        this.mode = MODE_CREATE;
        this.targetId = this.tree.displayDir();
        this.nameBox.setText("");
        this.nameHint = I18n.format("cnpclines.status.hint_new_folder");
        focusNameBox();
        this.status = I18n.format("cnpclines.status.create_prompt");
        disarmDelete();
    }

    private void beginRename() {
        if (!ensureOwnTarget()) {
            return;
        }
        if (isProtectedDir(EntryTreeView.pathOf(this.tree.displayDir()))) {
            this.status = I18n.format("cnpclines.status.protected_rename");
            return;
        }
        this.mode = MODE_RENAME;
        this.targetId = this.tree.displayDir();
        this.nameBox.setText(tail(EntryTreeView.pathOf(this.targetId)));
        this.nameHint = I18n.format("cnpclines.status.hint_new_name");
        focusNameBox();
        this.status = I18n.format("cnpclines.status.rename_prompt");
        disarmDelete();
    }

    private void focusNameBox() {
        this.nameBox.setFocused(true);
    }

    private void confirmName() {
        if (this.mode == MODE_NONE || this.targetId == null) {
            this.status = I18n.format("cnpclines.status.select_first");
            return;
        }
        String raw = this.nameBox.getText().trim();
        String name = CustomSkinsPack.sanitizeFolderName(raw);
        if (name.isEmpty()) {
            this.status = I18n.format("cnpclines.status.name_needs_char");
            return;
        }
        boolean adjusted = !name.equals(raw);
        String dir = EntryTreeView.pathOf(this.targetId);
        if (dir == null || dir.isEmpty()) {
            this.status = I18n.format("cnpclines.status.ns_root_op");
            this.mode = MODE_NONE;
            return;
        }
        if (this.mode == MODE_CREATE) {
            try {
                if (!CustomSkinsPack.createFolder(CustomSkinsPack.NAMESPACE, dir, name)) {
                    this.status = I18n.format("cnpclines.status.folder_exists");
                    return;
                }
                ResourceIndex.refresh();
                this.tree.configure(MAIN_PATHS[this.tab], this.tree.currentDir());
                this.tree.restoreSelection(this.targetId);
                refreshList();
                this.status = I18n.format(adjusted
                    ? "cnpclines.status.created_adjusted"
                    : "cnpclines.status.created", name);
                playClick();
            } catch (Exception e) {
                this.status = I18n.format("cnpclines.status.create_failed", String.valueOf(e.getMessage()));
            }
        } else {
            if (isProtectedDir(dir)) {
                this.status = I18n.format("cnpclines.status.protected_rename");
                return;
            }
            try {
                String error = CustomSkinsPack.renameFolder(CustomSkinsPack.NAMESPACE, dir, name);
                if (error != null) {
                    this.status = error;
                    return;
                }
                String newDir = parentDirOf(dir) + name + "/";
                String newId = CustomSkinsPack.NAMESPACE + ":" + newDir;
                ResourceIndex.refresh();
                this.tree.configure(MAIN_PATHS[this.tab], this.tree.currentDir());
                this.tree.restoreSelection(newId);
                refreshList();
                this.status = I18n.format(adjusted
                    ? "cnpclines.status.renamed_adjusted"
                    : "cnpclines.status.renamed", name);
                playClick();
            } catch (Exception e) {
                this.status = I18n.format("cnpclines.status.rename_failed", String.valueOf(e.getMessage()));
            }
        }
        this.mode = MODE_NONE;
        this.targetId = null;
        this.nameBox.setText("");
        this.nameHint = "";
        this.nameBox.setFocused(false);
    }

    private void pressDelete() {
        if (this.armedDelete) {
            runPendingDelete();
            disarmDelete();
            return;
        }
        this.pendingFile = null;
        this.pendingDir = null;
        if (this.deleteFocus == FOCUS_LIST && this.list.selectedLocation() != null) {
            ResourceLocation location = this.list.selectedLocation();
            String original = ResourceIndex.toOriginal(location.getNamespace(), location.getPath());
            if (!CustomSkinsPack.ownsFile(location.getNamespace(), original)) {
                this.status = I18n.format("cnpclines.status.other_pack_file_delete");
                return;
            }
            this.pendingFile = location;
        } else if (this.tree.selectedDir() != null) {
            String id = this.tree.selectedDir();
            String ns = EntryTreeView.nsOf(id);
            String path = EntryTreeView.pathOf(id);
            if (!CustomSkinsPack.NAMESPACE.equals(ns)) {
                this.status = I18n.format("cnpclines.status.other_pack_folder_delete");
                return;
            }
            if (path == null || path.isEmpty()) {
                this.status = I18n.format("cnpclines.status.ns_root_delete");
                return;
            }
            if (isProtectedDir(path)) {
                this.status = I18n.format("cnpclines.status.protected_delete");
                return;
            }
            if (!CustomSkinsPack.owns(ns, path)) {
                this.status = I18n.format("cnpclines.status.other_pack_folder_delete");
                return;
            }
            this.pendingDir = id;
        } else {
            this.status = I18n.format("cnpclines.status.select_target");
            return;
        }
        this.armedDelete = true;
        this.armedAt = System.currentTimeMillis();
        this.deleteButton.displayString = I18n.format("cnpclines.manager.confirm_delete");
        this.status = I18n.format("cnpclines.status.confirm_prompt");
    }

    private void runPendingDelete() {
        if (this.pendingFile != null) {
            ResourceLocation location = this.pendingFile;
            this.pendingFile = null;
            deleteFile(location);
            return;
        }
        if (this.pendingDir != null) {
            String id = this.pendingDir;
            this.pendingDir = null;
            doDeleteFolder(id);
        }
    }

    private void doDeleteFolder(String id) {
        String ns = EntryTreeView.nsOf(id);
        String path = EntryTreeView.pathOf(id);
        try {
            String error = CustomSkinsPack.deleteFolder(ns, path);
            if (error != null) {
                this.status = error;
                return;
            }
            String parent = EntryTreeView.parentOf(id);
            if (parent == null || !EntryTreeView.exists(parent)) {
                parent = MAIN_PATHS[this.tab];
            }
            ResourceIndex.refresh();
            this.tree.configure(MAIN_PATHS[this.tab], parent);
            refreshList();
            this.status = I18n.format("cnpclines.status.deleted_folder", tail(path));
            playClick();
        } catch (Exception e) {
            this.status = I18n.format("cnpclines.status.delete_failed", String.valueOf(e.getMessage()));
        }
    }

    private void deleteFile(ResourceLocation location) {
        try {
            String original = ResourceIndex.toOriginal(location.getNamespace(), location.getPath());
            String error = CustomSkinsPack.deleteFile(location.getNamespace(), original);
            if (error != null) {
                this.status = error;
                return;
            }
            ResourceIndex.refresh();
            refreshList();
            int slash = original.lastIndexOf('/');
            this.status = I18n.format("cnpclines.status.deleted_file", original.substring(slash + 1));
            playClick();
        } catch (Exception e) {
            this.status = I18n.format("cnpclines.status.delete_failed", String.valueOf(e.getMessage()));
        }
    }

    private void disarmDelete() {
        this.pendingFile = null;
        this.pendingDir = null;
        if (!this.armedDelete) {
            return;
        }
        this.armedDelete = false;
        if (this.deleteButton != null) {
            this.deleteButton.displayString = I18n.format("cnpclines.manager.delete");
        }
    }

    private void playClick() {
        this.mc.getSoundHandler()
            .playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private void drawPanel() {
        int x = this.guiLeft;
        int y = this.guiTop;
        Gui.drawRect(x, y, x + PANEL_W, y + PANEL_H, COL_BG);
        Gui.drawRect(x, y, x + PANEL_W, y + 1, COL_LIGHT);
        Gui.drawRect(x, y, x + 1, y + PANEL_H, COL_LIGHT);
        Gui.drawRect(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, COL_DARK);
        Gui.drawRect(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, COL_DARK);

        String title = I18n.format("cnpclines.manager.title");
        this.fontRenderer.drawString(title, x + 8, y + 7, COL_TEXT);
        int pathX = 8 + this.fontRenderer.getStringWidth(title) + 6;
        String path = EntryTreeView.displayPath(this.tree.displayDir());
        if (this.tree.hoveringFolder()) {
            path = path + I18n.format("cnpclines.editor.dblclick");
        }
        this.fontRenderer.drawString(TextureEntryList.ellipsize(this.fontRenderer, path, PANEL_W - 6 - pathX),
            x + pathX, y + 7, COL_TEXT);

        Gui.drawRect(x + 8, y + 180, x + 412, y + 181, COL_DIVIDER);
        String line = this.status == null || this.status.isEmpty()
            ? I18n.format("cnpclines.manager.ready") : this.status;
        this.fontRenderer.drawString(TextureEntryList.ellipsize(this.fontRenderer, line, 332),
            x + 8, y + 187, COL_TEXT);
    }

    private void drawControls() {
        int x = this.guiLeft;
        int y = this.guiTop;
        this.fontRenderer.drawString(TextureEntryList.ellipsize(this.fontRenderer,
            I18n.format("cnpclines.manager.ctrlv"), 96),
            x + 316, y + 110, 0xFF707070);
        this.fontRenderer.drawString(TextureEntryList.ellipsize(this.fontRenderer,
            I18n.format("cnpclines.manager.swipe"), 96),
            x + 316, y + 122, 0xFF707070);
        String hint;
        if (this.mode == MODE_CREATE) {
            hint = I18n.format("cnpclines.manager.hint_new",
                safeTail(EntryTreeView.pathOf(this.targetId)));
        } else if (this.mode == MODE_RENAME) {
            hint = I18n.format("cnpclines.manager.hint_rename",
                safeTail(EntryTreeView.pathOf(this.targetId)));
        } else {
            hint = "";
        }
        if (!hint.isEmpty()) {
            this.fontRenderer.drawString(TextureEntryList.ellipsize(this.fontRenderer, hint, 96),
                x + 316, y + 140, COL_HINT);
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
            this.fontRenderer.drawString(I18n.format(TAB_KEYS[i]), tx + 26, this.guiTop - 4, 0xFFFFFFFF);
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

    private boolean inside(GuiButton widget, double mouseX, double mouseY) {
        return mouseX >= widget.x
            && mouseX < widget.x + widget.width
            && mouseY >= widget.y
            && mouseY < widget.y + widget.height;
    }

    private boolean inside(GuiTextField widget, double mouseX, double mouseY) {
        return mouseX >= widget.x
            && mouseX < widget.x + widget.width
            && mouseY >= widget.y
            && mouseY < widget.y + widget.height;
    }

    private static boolean isProtectedDir(String dir) {
        return dir.equals("textures/")
            || dir.equals("textures/entity/")
            || dir.equals("textures/entity/humanmale/")
            || dir.equals("textures/cloak/")
            || dir.equals("textures/overlays/");
    }

    private static String parentDirOf(String dir) {
        int lastSlash = dir.lastIndexOf('/', dir.length() - 2);
        return lastSlash < 0 ? "" : dir.substring(0, lastSlash + 1);
    }

    private static String tail(String dir) {
        if (dir == null || dir.isEmpty()) {
            return "";
        }
        int end = dir.endsWith("/") ? dir.length() - 1 : dir.length();
        int start = dir.lastIndexOf('/', end - 1) + 1;
        return dir.substring(start, end);
    }

    private static String safeTail(String dir) {
        String value = tail(dir);
        return value.isEmpty() ? dir : value;
    }
}
