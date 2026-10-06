package top.cnpclines.client.gui;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import top.cnpclines.client.CustomSkinsPack;

public class GuiSkinsManager extends Screen {

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
        ResourceLocation.fromNamespaceAndPath("cnpclines", "textures/overlay/message/icons/skins.png"),
        ResourceLocation.fromNamespaceAndPath("cnpclines", "textures/overlay/message/icons/cloak.png"),
        ResourceLocation.fromNamespaceAndPath("cnpclines", "textures/overlay/message/icons/overlays.png"),
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

    private final GuiBetterMaterialEditor parent;

    private int guiLeft;
    private int guiTop;
    private int tab;

    private EntryTreeView tree;
    private TextureEntryList list;
    private EditBox nameBox;
    private EditBox searchBox;
    private SearchResultView results;
    private Button deleteButton;

    private int mode = MODE_NONE;
    private String targetId;
    private boolean armedDelete;
    private long armedAt;
    private int deleteFocus = FOCUS_NONE;
    private ResourceLocation pendingFile;
    private String pendingDir;
    private String status = "";

    public GuiSkinsManager(GuiBetterMaterialEditor parent) {
        super(Component.literal("Skins 管理器"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.guiLeft = (this.width - PANEL_W) / 2;
        this.guiTop = (this.height - PANEL_H) / 2;

        ResourceIndex.ensureFresh();

        if (this.tree == null) {
            this.tree = new EntryTreeView(this.font);
            this.tree.setListener(new EntryTreeView.Listener() {
                @Override
                public void onDirChosen() {
                    deleteFocus = FOCUS_TREE;
                    refreshList();
                    disarmDelete();
                }
            });
            this.list = new TextureEntryList(this.font);
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
            this.results = new SearchResultView(this.font);
            this.results.setListener(this::locate);
            this.results.setSkinThumbRenderer(parent::drawSkinThumb);
        }
        this.tree.setBounds(this.guiLeft + 6, this.guiTop + 38, 108, 138);
        this.list.setBounds(this.guiLeft + 120, this.guiTop + 38, 190, 138);
        this.results.setBounds(this.guiLeft + 6, this.guiTop + 38, 304, 138);
        refreshList();

        this.searchBox = new EditBox(this.font, this.guiLeft + 6, this.guiTop + 20, 304, 16,
            Component.literal(""));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(Component.literal("搜索皮肤：文件名 / 拼音 / 首字母（如 hs → 红色）"));
        this.searchBox.setResponder(value -> refreshSearch());
        this.addRenderableWidget(this.searchBox);
        refreshSearch();

        Button create = Button.builder(Component.literal("新建文件夹"), button -> beginCreate())
            .bounds(this.guiLeft + 316, this.guiTop + 20, 98, 18).build();
        create.setTooltip(Tooltip.create(Component.literal(
            "在当前文件夹内新建子文件夹\n名称只能使用小写字母、数字、下划线")));
        this.addRenderableWidget(create);

        Button rename = Button.builder(Component.literal("重命名"), button -> beginRename())
            .bounds(this.guiLeft + 316, this.guiTop + 41, 98, 18).build();
        rename.setTooltip(Tooltip.create(Component.literal(
            "重命名当前文件夹\n名称只能使用小写字母、数字、下划线\n仅对本资源包内的文件夹生效")));
        this.addRenderableWidget(rename);

        this.deleteButton = Button.builder(Component.literal("删除"), button -> pressDelete())
            .bounds(this.guiLeft + 316, this.guiTop + 62, 98, 18).build();
        this.deleteButton.setTooltip(Tooltip.create(Component.literal(
            "删除左侧选中的文件夹或右侧选中的条目（需二次确认）\n仅删除本资源包内的文件")));
        this.addRenderableWidget(this.deleteButton);

        this.nameBox = new EditBox(this.font, this.guiLeft + 316, this.guiTop + 84, 66, 18,
            Component.literal(""));
        this.nameBox.setMaxLength(32);
        this.addRenderableWidget(this.nameBox);
        this.addRenderableWidget(Button.builder(Component.literal("确定"), button -> confirmName())
            .bounds(this.guiLeft + 384, this.guiTop + 84, 30, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("返回"), button -> close())
            .bounds(this.guiLeft + 350, this.guiTop + 206, 64, 20).build());
    }

    private void refreshList() {
        this.list.setPreviewMode(this.tab);
        this.list.setSwipeEnabled(true);
        String dir = this.tree.displayDir();
        this.list.setItems(ResourceIndex.filesIn(EntryTreeView.nsOf(dir), EntryTreeView.pathOf(dir)));
        this.list.setHighlight(this.parent.currentTextureFor(this.tab));
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
        if (this.armedDelete && System.currentTimeMillis() - this.armedAt > 4000) {
            disarmDelete();
        }
        this.backgroundRendered = false;
        renderBackground(graphics, mouseX, mouseY, partialTick);
        drawTabsBack(graphics);
        drawPanel(graphics);
        drawTabsFront(graphics);
        if (isSearchActive()) {
            this.results.render(graphics, mouseX, mouseY);
        } else {
            this.tree.render(graphics, mouseX, mouseY);
            this.list.render(graphics, mouseX, mouseY);
        }
        drawControls(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = tabAt(mouseX, mouseY);
            if (index >= 0) {
                switchTab(index);
                return true;
            }
            if (this.armedDelete && !inside(this.deleteButton, mouseX, mouseY)
                && !inside(this.nameBox, mouseX, mouseY)) {
                disarmDelete();
            }
        }
        if (isSearchActive()) {
            if (this.results.mouseClicked(mouseX, mouseY, button)) return true;
        } else {
            if (this.tree.mouseClicked(mouseX, mouseY, button)) return true;
            if (this.list.mouseClicked(mouseX, mouseY, button)) return true;
        }
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
        if (isSearchActive()) {
            if (this.results.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        } else {
            if (this.tree.mouseScrolled(mouseX, mouseY, scrollY)) return true;
            if (this.list.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        String id = this.tree.displayDir();
        String dir = EntryTreeView.pathOf(id);
        if (!CustomSkinsPack.NAMESPACE.equals(EntryTreeView.nsOf(id))
            || dir == null || dir.isEmpty()) {
            this.status = "该文件夹来自其他资源包，无法导入";
            return;
        }
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

    @Override
    public void onClose() {
        close();
    }

    private void close() {
        this.minecraft.setScreen(this.parent);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                SearchResultView.Row row = this.results.first();
                if (row != null) {
                    locate(row.location(), row.dirId());
                }
                return true;
            }
            if (keyCode == 256 && isSearchActive()) {
                clearSearch();
                return true;
            }
        }
        if (this.nameBox != null && this.nameBox.isFocused()
            && this.mode != MODE_NONE && (keyCode == 257 || keyCode == 335)) {
            confirmName();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isSearchActive() {
        return this.searchBox != null && !this.searchBox.getValue().trim().isEmpty()
            && this.results != null;
    }

    private void refreshSearch() {
        if (this.results == null) {
            return;
        }
        String q = this.searchBox == null ? "" : this.searchBox.getValue().trim();
        String scope = searchScopeId();
        this.results.setScope(EntryTreeView.displayPath(scope));
        this.results.setPreviewMode(this.tab);
        this.results.setHighlight(this.parent.currentTextureFor(this.tab));
        this.results.setResults(q.isEmpty() ? java.util.List.of() : SkinSearch.query(q, scope));
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
        this.searchBox.setValue("");
        this.searchBox.setFocused(false);
    }

    private void locate(ResourceLocation location, String dirId) {
        int nextTab = SkinSearch.tabForPath(dirId);
        if (nextTab >= 0 && nextTab != this.tab) {
            this.tab = nextTab;
            this.mode = MODE_NONE;
            this.targetId = null;
            disarmDelete();
        }
        this.searchBox.setValue("");
        this.searchBox.setFocused(false);
        this.tree.configure(MAIN_PATHS[this.tab], dirId);
        this.tree.restoreSelection(dirId);
        refreshList();
        this.list.setSelected(location);
        this.status = "已定位 " + EntryTreeView.displayPath(dirId);
        playClick();
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
        this.nameBox.setValue("");
        this.nameBox.setHint(Component.literal("新文件夹名"));
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
        this.nameBox.setValue(tail(EntryTreeView.pathOf(this.targetId)));
        this.nameBox.setHint(Component.literal("新名称"));
        focusNameBox();
        this.status = "输入新名称后点 确定（或回车）重命名";
        disarmDelete();
    }

    private void focusNameBox() {
        this.setFocused(this.nameBox);
        this.nameBox.setFocused(true);
    }

    private void confirmName() {
        if (this.mode == MODE_NONE || this.targetId == null) {
            this.status = "请先点击 新建文件夹 或 重命名";
            return;
        }
        String raw = this.nameBox.getValue().trim();
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
        this.nameBox.setValue("");
        this.nameBox.setHint(Component.literal(""));
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
        this.deleteButton.setMessage(Component.literal("确认删除?"));
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
            this.deleteButton.setMessage(Component.literal("删除"));
        }
    }

    private void playClick() {
        this.minecraft.getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private void drawPanel(GuiGraphics graphics) {
        int x = this.guiLeft;
        int y = this.guiTop;
        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, COL_BG);
        graphics.fill(x, y, x + PANEL_W, y + 1, COL_LIGHT);
        graphics.fill(x, y, x + 1, y + PANEL_H, COL_LIGHT);
        graphics.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, COL_DARK);
        graphics.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, COL_DARK);

        graphics.drawString(this.font, "Skins 管理器", x + 8, y + 7, COL_TEXT, false);
        int pathX = 8 + this.font.width("Skins 管理器") + 6;
        String path = EntryTreeView.displayPath(this.tree.displayDir());
        if (this.tree.hoveringFolder()) {
            path = path + "（双击打开）";
        }
        graphics.drawString(this.font, TextureEntryList.ellipsize(this.font, path, PANEL_W - 6 - pathX),
            x + pathX, y + 7, COL_TEXT, false);

        graphics.fill(x + 8, y + 180, x + 412, y + 181, COL_DIVIDER);
        String line = this.status == null || this.status.isEmpty() ? "就绪" : this.status;
        graphics.drawString(this.font, TextureEntryList.ellipsize(this.font, line, 332),
            x + 8, y + 187, COL_TEXT, false);
    }

    private void drawControls(GuiGraphics graphics) {
        int x = this.guiLeft;
        int y = this.guiTop;
        graphics.drawString(this.font, TextureEntryList.ellipsize(this.font, "拖入 PNG 导入当前目录", 96),
            x + 316, y + 110, 0xFF707070, false);
        graphics.drawString(this.font, TextureEntryList.ellipsize(this.font, "长按左滑条目删除", 96),
            x + 316, y + 122, 0xFF707070, false);
        String hint = switch (this.mode) {
            case MODE_CREATE -> "新建: " + safeTail(EntryTreeView.pathOf(this.targetId));
            case MODE_RENAME -> "重命名: " + safeTail(EntryTreeView.pathOf(this.targetId));
            default -> "";
        };
        if (!hint.isEmpty()) {
            graphics.drawString(this.font, TextureEntryList.ellipsize(this.font, hint, 96),
                x + 316, y + 140, COL_HINT, false);
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
            graphics.drawString(this.font, TAB_NAMES[i], tx + 26, this.guiTop - 4,
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
        graphics.drawString(this.font, TAB_NAMES[i], tx + 26, ty + 4, 0xFF000000, false);
        graphics.blit(TabIcons.black(TAB_ICONS[i]), tx + 8, ty + 1, 16, 16,
            0.0F, 0.0F, 16, 16, 16, 16);
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

    private boolean inside(GuiEventListener widget, double mouseX, double mouseY) {
        if (widget instanceof AbstractWidget abstractWidget) {
            return mouseX >= abstractWidget.getX()
                && mouseX < abstractWidget.getX() + abstractWidget.getWidth()
                && mouseY >= abstractWidget.getY()
                && mouseY < abstractWidget.getY() + abstractWidget.getHeight();
        }
        return false;
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
