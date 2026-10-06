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
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import top.cnpclines.client.CustomSkinsPack;

public class GuiSkinsManager extends GuiScreen {

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

    private static final String[] TAB_NAMES = {"皮肤", "披风", "附加材质"};
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
    private GuiButton deleteButton;
    private String nameHint = "";

    private int mode = MODE_NONE;
    private String targetId;
    private boolean armedDelete;
    private long armedAt;
    private int deleteFocus = FOCUS_NONE;
    private ResourceLocation pendingFile;
    private String pendingDir;
    private String status = "";
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
        }
        this.tree.setBounds(this.guiLeft + 6, this.guiTop + 20, 108, 156);
        this.list.setBounds(this.guiLeft + 120, this.guiTop + 20, 190, 156);
        refreshList();

        this.buttonList.add(new GuiButton(BTN_CREATE, this.guiLeft + 316, this.guiTop + 20, 98, 18, "新建文件夹"));
        this.buttonList.add(new GuiButton(BTN_RENAME, this.guiLeft + 316, this.guiTop + 41, 98, 18, "重命名"));
        this.deleteButton = new GuiButton(BTN_DELETE, this.guiLeft + 316, this.guiTop + 62, 98, 18, "删除");
        this.buttonList.add(this.deleteButton);

        this.nameBox = new GuiTextField(0, this.fontRenderer, this.guiLeft + 316, this.guiTop + 84, 66, 18);
        this.nameBox.setMaxStringLength(32);

        this.buttonList.add(new GuiButton(BTN_OK, this.guiLeft + 384, this.guiTop + 84, 30, 18, "确定"));
        this.buttonList.add(new GuiButton(BTN_IMPORT, this.guiLeft + 316, this.guiTop + 150, 98, 18, "导入PNG..."));
        this.buttonList.add(new GuiButton(BTN_BACK, this.guiLeft + 350, this.guiTop + 206, 64, 20, "返回"));
    }

    private void refreshList() {
        this.list.setPreviewMode(this.tab);
        this.list.setSwipeEnabled(true);
        String dir = this.tree.displayDir();
        this.list.setItems(ResourceIndex.filesIn(EntryTreeView.nsOf(dir), EntryTreeView.pathOf(dir)));
        this.list.setHighlight(this.parent.currentTextureFor(this.tab));
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
        this.tree.render(mouseX, mouseY);
        this.list.render(mouseX, mouseY);
        drawControls();
        drawNameBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawTooltips(mouseX, mouseY);
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
            tip = "删除左侧选中的文件夹或右侧选中的条目（需二次确认）\n仅删除本资源包内的文件";
        } else if (mouseOverButton(BTN_CREATE, mouseX, mouseY)) {
            tip = "在当前文件夹内新建子文件夹\n名称只能使用小写字母、数字、下划线";
        } else if (mouseOverButton(BTN_RENAME, mouseX, mouseY)) {
            tip = "重命名当前文件夹\n名称只能使用小写字母、数字、下划线\n仅对本资源包内的文件夹生效";
        } else if (mouseOverButton(BTN_IMPORT, mouseX, mouseY)) {
            tip = "打开系统文件选择框，批量导入 PNG\n导入到当前文件夹（仅本资源包）";
        }
        if (tip != null) {
            this.drawHoveringText(Arrays.asList(tip.split("\n")), mouseX, mouseY);
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
        if (this.tree.mouseClicked(mouseX, mouseY, mouseButton)) return;
        if (this.list.mouseClicked(mouseX, mouseY, mouseButton)) return;
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
            if (this.tree.mouseScrolled(mouseX, mouseY, delta)) return;
            if (this.list.mouseScrolled(mouseX, mouseY, delta)) return;
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
            this.status = "该文件夹来自其他资源包，无法导入";
            return;
        }
        String clipboard = GuiScreen.getClipboardString();
        if (clipboard == null || clipboard.trim().isEmpty()) {
            this.status = "剪贴板为空（复制 PNG 文件后 Ctrl+V）";
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
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
                chooser.setDialogTitle("导入 PNG 皮肤");
                chooser.setMultiSelectionEnabled(true);
                chooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "PNG 图片 (*.png)", "png"));
                if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                    List<Path> paths = new ArrayList<>();
                    for (java.io.File file : chooser.getSelectedFiles()) {
                        paths.add(file.toPath());
                    }
                    if (!paths.isEmpty()) {
                        this.pendingImportFiles = paths;
                    }
                }
            } catch (Throwable ignored) {
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
            this.status = "该文件夹来自其他资源包，无法导入";
            return;
        }
        importPngTo(id, dir, paths);
    }

    private void importPngTo(String id, String dir, List<Path> paths) {
        try {
            CustomSkinsPack.ImportResult result = CustomSkinsPack.importPng(dir, paths);
            if (result.added() <= 0 && result.skipped() <= 0) {
                this.status = "没有可导入的文件";
                return;
            }
            StringBuilder message = new StringBuilder();
            if (result.added() > 0) {
                message.append("已导入 ").append(result.added()).append(" 个: ");
                List<String> names = result.names();
                for (int i = 0; i < names.size() && i < 2; i++) {
                    if (i > 0) {
                        message.append("、");
                    }
                    message.append(names.get(i));
                }
                if (names.size() > 2) {
                    message.append(" 等").append(names.size()).append("个");
                }
                ResourceIndex.refresh();
                this.tree.configure(MAIN_PATHS[this.tab], this.tree.currentDir());
                this.tree.restoreSelection(id);
                refreshList();
                playClick();
            }
            if (result.skipped() > 0) {
                if (message.length() > 0) {
                    message.append("；");
                }
                message.append("跳过 ").append(result.skipped()).append(" 个（非 PNG 或读取失败）");
            }
            this.status = message.toString();
        } catch (Exception e) {
            this.status = "导入失败: " + e.getMessage();
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
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
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
            this.status = "该文件夹来自其他资源包，无法操作";
            return false;
        }
        String path = EntryTreeView.pathOf(id);
        if (path == null || path.isEmpty()) {
            this.status = "命名空间根目录不可操作";
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
        this.nameHint = "新文件夹名";
        focusNameBox();
        this.status = "输入名称后点 确定（或回车）创建，大写/空格/符号会自动规范为小写";
        disarmDelete();
    }

    private void beginRename() {
        if (!ensureOwnTarget()) {
            return;
        }
        if (isProtectedDir(EntryTreeView.pathOf(this.tree.displayDir()))) {
            this.status = "系统内置文件夹不可重命名";
            return;
        }
        this.mode = MODE_RENAME;
        this.targetId = this.tree.displayDir();
        this.nameBox.setText(tail(EntryTreeView.pathOf(this.targetId)));
        this.nameHint = "新名称";
        focusNameBox();
        this.status = "输入新名称后点 确定（或回车）重命名";
        disarmDelete();
    }

    private void focusNameBox() {
        this.nameBox.setFocused(true);
    }

    private void confirmName() {
        if (this.mode == MODE_NONE || this.targetId == null) {
            this.status = "请先点击 新建文件夹 或 重命名";
            return;
        }
        String raw = this.nameBox.getText().trim();
        String name = CustomSkinsPack.sanitizeFolderName(raw);
        if (name.isEmpty()) {
            this.status = "名称需包含小写字母或数字";
            return;
        }
        boolean adjusted = !name.equals(raw);
        String dir = EntryTreeView.pathOf(this.targetId);
        if (dir == null || dir.isEmpty()) {
            this.status = "命名空间根目录不可操作";
            this.mode = MODE_NONE;
            return;
        }
        if (this.mode == MODE_CREATE) {
            try {
                if (!CustomSkinsPack.createFolder(CustomSkinsPack.NAMESPACE, dir, name)) {
                    this.status = "创建失败：文件夹已存在";
                    return;
                }
                ResourceIndex.refresh();
                this.tree.configure(MAIN_PATHS[this.tab], this.tree.currentDir());
                this.tree.restoreSelection(this.targetId);
                refreshList();
                this.status = adjusted
                    ? "已创建 " + name + "/（名称已自动调整）"
                    : "已创建 " + name + "/";
                playClick();
            } catch (Exception e) {
                this.status = "创建失败: " + e.getMessage();
            }
        } else {
            if (isProtectedDir(dir)) {
                this.status = "系统内置文件夹不可重命名";
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
                this.status = adjusted
                    ? "已重命名为 " + name + "/（名称已自动调整）"
                    : "已重命名为 " + name + "/";
                playClick();
            } catch (Exception e) {
                this.status = "重命名失败: " + e.getMessage();
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
                this.status = "该文件来自其他资源包，无法删除";
                return;
            }
            this.pendingFile = location;
        } else if (this.tree.selectedDir() != null) {
            String id = this.tree.selectedDir();
            String ns = EntryTreeView.nsOf(id);
            String path = EntryTreeView.pathOf(id);
            if (!CustomSkinsPack.NAMESPACE.equals(ns)) {
                this.status = "该文件夹来自其他资源包，无法删除";
                return;
            }
            if (path == null || path.isEmpty()) {
                this.status = "命名空间根目录不可删除";
                return;
            }
            if (isProtectedDir(path)) {
                this.status = "系统内置文件夹不可删除";
                return;
            }
            if (!CustomSkinsPack.owns(ns, path)) {
                this.status = "该文件夹来自其他资源包，无法删除";
                return;
            }
            this.pendingDir = id;
        } else {
            this.status = "请先在左侧选中文件夹或右侧选中条目";
            return;
        }
        this.armedDelete = true;
        this.armedAt = System.currentTimeMillis();
        this.deleteButton.displayString = "确认删除?";
        this.status = "再次点击 删除 以确认";
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
            this.status = "已删除 " + tail(path) + "（仅本包）";
            playClick();
        } catch (Exception e) {
            this.status = "删除失败: " + e.getMessage();
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
            this.status = "已删除 " + original.substring(slash + 1);
            playClick();
        } catch (Exception e) {
            this.status = "删除失败: " + e.getMessage();
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
            this.deleteButton.displayString = "删除";
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

        this.fontRenderer.drawString("Skins 管理器", x + 8, y + 7, COL_TEXT);
        int pathX = 8 + this.fontRenderer.getStringWidth("Skins 管理器") + 6;
        String path = EntryTreeView.displayPath(this.tree.displayDir());
        if (this.tree.hoveringFolder()) {
            path = path + "（双击打开）";
        }
        this.fontRenderer.drawString(TextureEntryList.ellipsize(this.fontRenderer, path, PANEL_W - 6 - pathX),
            x + pathX, y + 7, COL_TEXT);

        Gui.drawRect(x + 8, y + 180, x + 412, y + 181, COL_DIVIDER);
        String line = this.status == null || this.status.isEmpty() ? "就绪" : this.status;
        this.fontRenderer.drawString(TextureEntryList.ellipsize(this.fontRenderer, line, 332),
            x + 8, y + 187, COL_TEXT);
    }

    private void drawControls() {
        int x = this.guiLeft;
        int y = this.guiTop;
        this.fontRenderer.drawString(TextureEntryList.ellipsize(this.fontRenderer, "Ctrl+V 粘贴 PNG 导入", 96),
            x + 316, y + 110, 0xFF707070);
        this.fontRenderer.drawString(TextureEntryList.ellipsize(this.fontRenderer, "长按左滑条目删除", 96),
            x + 316, y + 122, 0xFF707070);
        String hint;
        if (this.mode == MODE_CREATE) {
            hint = "新建: " + safeTail(EntryTreeView.pathOf(this.targetId));
        } else if (this.mode == MODE_RENAME) {
            hint = "重命名: " + safeTail(EntryTreeView.pathOf(this.targetId));
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
            this.fontRenderer.drawString(TAB_NAMES[i], tx + 26, this.guiTop - 4, 0xFFFFFFFF);
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
        this.fontRenderer.drawString(TAB_NAMES[i], tx + 26, ty + 4, COL_TEXT);
        drawIcon(TAB_ICONS[i], tx + 8, ty + 1);
    }

    private void drawIcon(ResourceLocation rl, int x, int y) {
        this.mc.getTextureManager().bindTexture(rl);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    private int[][] tabRects() {
        int top = Math.max(0, this.guiTop - TAB_H);
        int height = this.guiTop + 1 - top;
        int[][] rects = new int[TAB_NAMES.length][4];
        int tx = this.guiLeft + 6;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tw = tabWidth(TAB_NAMES[i]);
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
