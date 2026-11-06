package top.cnpclines.faction.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import noppes.npcs.client.gui.global.GuiNPCManageFactions;
import noppes.npcs.client.gui.player.GuiQuestLog;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketFactionsGet;
import top.cnpcplus.text.StyledText;
import top.cnpclines.client.gui.GuiBetterMaterialEditor;
import top.cnpclines.client.gui.ResourceIndex;
import top.cnpclines.client.gui.TexturePreviewLoader;
import top.cnpclines.faction.FactionPackets;
import top.cnpclines.faction.FactionLayout;
import top.cnpclines.faction.FactionStanding;
import top.cnpclines.faction.FactionVisual;
import net.neoforged.neoforge.network.PacketDistributor;

/** Player and editor share the same layout; editable drafts never change CNPC points. */
public final class FactionScreen extends Screen {
    private static final ResourceLocation HEART_CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART_WITHERED = ResourceLocation.withDefaultNamespace("hud/heart/withered_full");
    private static final ResourceLocation HEART_FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private boolean backgroundRendered;
    private final GuiNPCManageFactions parent;
    private final Map<Integer, CompoundTag> drafts = new HashMap<>();
    private final Map<Integer, Integer> points = new HashMap<>();
    private final List<Faction> factions = new ArrayList<>();
    private int selected = -1, tick, x, y, w, h, leftW, scroll, descriptionScroll;
    private int dragId = -1;
    private boolean scrolling, moving, loaded, allowed, saving;
    private double pressY;
    private String status = "";
    private Button saveButton;
    private float uiScale = 1;
    private FactionTextPanel textPanel;

    public FactionScreen(GuiNPCManageFactions parent, Faction selectedFaction) {
        super(Component.translatable("menu.factions"));
        this.parent = parent;
        if (selectedFaction != null) this.selected = selectedFaction.id;
    }

    private boolean edit() { return parent != null; }
    private boolean editable() { return edit() && allowed && !saving; }

    @Override protected void init() {
        int sidebar = textPanel == null ? 0 : FactionTextPanel.widthFor(width) + 8;
        FactionLayout layout = FactionLayout.fit(width - sidebar, height, edit());
        uiScale = layout.scale();
        w = FactionLayout.WIDTH;
        h = FactionLayout.HEIGHT;
        x = layout.x() + (int)Math.ceil(sidebar / uiScale);
        y = layout.y();
        leftW = FactionLayout.LEFT;
        if (edit()) {
            saveButton = addRenderableWidget(Button.builder(Component.translatable("cnpclines.common.save"), b -> save())
                .bounds(x + 58, y + h + 16, 74, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("cnpclines.common.cancel"), b -> onClose())
                .bounds(x + 136, y + h + 16, 74, 20).build());
            saveButton.active = editable();
        }
        if (textPanel != null) textPanel.init(sidebar - 8, height);
        request();
    }

    private void request() { PacketDistributor.sendToServer(new FactionPackets.Request(edit())); }
    @Override public void tick() {
        if (textPanel != null) textPanel.tick();
        if (++tick % 20 == 0 && !saving) request();
    }

    public void receive(CompoundTag snapshot) {
        String error = snapshot.getString("Error");
        if (!error.isEmpty()) { status = I18n.get(error); saving = false; }
        allowed = snapshot.getBoolean("Edit");
        if (edit() && !allowed) status = I18n.get("cnpclines.faction.denied");
        ListTag list = snapshot.getList("Factions", 10);
        points.clear();
        if (!edit() || !loaded || snapshot.getBoolean("Saved")) {
            factions.clear();
            drafts.clear();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompound(i);
                Faction faction = new Faction();
                faction.readNBT(tag);
                factions.add(faction);
                CompoundTag draft = tag.getCompound(FactionVisual.KEY).copy();
                draft.remove("ListIcon");
                draft.remove("ListIconSpans");
                draft.remove("ListIconSize");
                drafts.put(faction.id, draft);
            }
            factions.sort(Comparator.comparingInt((Faction f) -> visual(f).contains("Order")
                ? visual(f).getInt("Order") : f.id).thenComparingInt(f -> f.id));
            loaded = true;
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            points.put(tag.getInt("Slot"), tag.getInt("Points"));
        }
        if (current() == null) selected = factions.isEmpty() ? -1 : factions.get(0).id;
        clampScroll();
        if (saveButton != null) saveButton.active = editable() && !factions.isEmpty();
        if (saving && snapshot.getBoolean("Saved")) {
            saving = false;
            Faction chosen = current();
            if (chosen != null) {
                ((FactionVisual)chosen).cnpclines$visual(visual(chosen));
                parent.setGuiData(chosen.writeNBT(new CompoundTag()));
            }
            minecraft.setScreen(parent);
            Packets.sendServer(new SPacketFactionsGet());
        }
    }

    private Faction current() { return factions.stream().filter(f -> f.id == selected).findFirst().orElse(null); }
    private CompoundTag visual(Faction faction) { return drafts.get(faction.id); }
    private int currentPoints(Faction f) { return points.getOrDefault(f.id, f.defaultPoints); }
    private int relation(Faction f) { return FactionStanding.relation(currentPoints(f), f.neutralPoints, f.friendlyPoints); }
    private String relationKey(Faction f) { return relation(f) < 0 ? "Hostile" : relation(f) == 0 ? "Neutral" : "Friendly"; }
    private int relationColor(Faction f) { return relation(f) < 0 ? 0xFF5555 : relation(f) == 0 ? 0xFFFF55 : 0x55FF55; }
    private StyledText text(Faction f, String key, String fallback) {
        CompoundTag v = visual(f);
        return new StyledText(v.contains(key) ? v.getString(key) : fallback, v.getString(key + "Spans")).legacy();
    }
    private StyledText name(Faction f) { return new StyledText(f.name, visual(f).getString("NameSpans")).legacy(); }
    private String relationFallback(String key) {
        return switch (key) {
            case "Friendly" -> I18n.get("faction.friendly");
            case "Hostile" -> I18n.get("faction.unfriendly");
            default -> I18n.get("faction.neutral");
        };
    }

    private int contentH() { return h - 16; }
    private int rightX() { return x + 100; }
    private int rightW() { return FactionLayout.RIGHT - 12; }
    private int flagH() { return FactionLayout.FLAG_HEIGHT; }
    private int flagW() { return FactionLayout.FLAG_WIDTH; }
    private int headerTextX() { return rightX() + flagW() + 8; }
    private int lineHeight(Faction f, String key) { return (int)Math.ceil(size(f, key) * 11 / 9f); }
    private int titleY() { return y + 20; }
    private int relationY(Faction f) { return titleY() + lineHeight(f, "Title") + 1; }
    private int pointsY(Faction f) { return relationY(f) + lineHeight(f, relationKey(f)) + 2; }
    private int descY() {
        Faction f = current();
        return f == null ? y + 79 : Math.max(y + 79, pointsY(f) + lineHeight(f, "Points") + 15);
    }
    private int barY() { return y + 184; }
    private int size(Faction f, String key) {
        return visual(f).contains(key + "Size") ? Math.max(6, Math.min(24, visual(f).getInt(key + "Size")))
            : key.equals("Title") || key.equals("Points") ? 11 : 10;
    }

    private void clip(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor((int)(x1 * uiScale), (int)(y1 * uiScale),
            (int)Math.ceil(x2 * uiScale), (int)Math.ceil(y2 * uiScale));
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        if (backgroundRendered) return;
        backgroundRendered = true;
        super.renderBackground(g, mx, my, partial);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        backgroundRendered = false;
        renderBackground(g, mx, my, partial);
        int actualMouseX = mx, actualMouseY = my;
        mx = (int)(mx / uiScale);
        my = (int)(my / uiScale);
        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1);
        if (!edit()) drawTabs(g);
        frame(g, x, y, w, h, 0xFFC6C6C6);
        frame(g, x + 8, y + 8, leftW, contentH(), 0xFF686868);
        frame(g, rightX() - 6, y + 8, FactionLayout.RIGHT, contentH(), 0xFF686868);
        drawList(g);
        Faction f = current();
        if (f == null) g.drawCenteredString(font, I18n.get(loaded ? "faction.nostanding" : "cnpclines.faction.loading"), x + w / 2, y + 40, 0xC6C6C6);
        else {
            String flag = visual(f).getString("Flag");
            drawFlag(g, flag, rightX(), y + 14, flagW(), flagH());
            if (edit() && flag.isEmpty()) drawText(g, new StyledText(I18n.get("cnpclines.faction.empty_flag"), ""),
                rightX() + 2, y + 23, flagW() - 4, flagH() - 12, 0xAAAAAA, 0, 8);
            drawText(g, editableText(f, "Title", f.name), headerTextX(), titleY(),
                rightX() + rightW() - headerTextX(), lineHeight(f, "Title"), 0xC6C6C6, 0, size(f, "Title"));
            drawText(g, text(f, relationKey(f), relationFallback(relationKey(f))), headerTextX(), relationY(f),
                rightX() + rightW() - headerTextX(), lineHeight(f, relationKey(f)), relationColor(f), 0, size(f, relationKey(f)));
            drawText(g, new StyledText(Integer.toString(currentPoints(f)), ""), headerTextX(), pointsY(f),
                rightX() + rightW() - headerTextX(), lineHeight(f, "Points"), barColor(f), 0, size(f, "Points"));
            g.fill(rightX(), descY() - 7, rightX() + rightW(), descY() - 6, 0xFF505050);
            drawText(g, editableText(f, "Description", ""), rightX(), descY(),
                rightW(), barY() - descY() - 20, 0xBBBBBB, descriptionScroll, size(f, "Description"));
            g.fill(rightX(), barY() - 12, rightX() + rightW(), barY() - 11, 0xFF505050);
            drawStanding(g, f);
        }
        if (edit()) {
            frame(g, x, y + h, w, 40, 0xFFC6C6C6);
            String hint = status.isEmpty() ? I18n.get("cnpclines.faction.edit_hint") : status;
            g.drawString(font, font.plainSubstrByWidth(hint, w - 12), x + 6, y + h + 5,
                status.isEmpty() ? 0x404040 : 0xAA2222, false);
        }
        super.render(g, mx, my, partial);
        g.pose().popPose();
        if (textPanel != null) textPanel.render(g, actualMouseX, actualMouseY, partial);
    }

    private StyledText editableText(Faction f, String key, String fallback) {
        StyledText value = text(f, key, fallback);
        return edit() && value.text().isEmpty()
            ? new StyledText(I18n.get(key.equals("Title") ? "cnpclines.faction.empty_title" : "cnpclines.faction.empty_text"), "") : value;
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
        int light = color == 0xFFC6C6C6 ? 0xFFFFFFFF : 0xFF8B8B8B;
        int edge = color == 0xFFC6C6C6 ? 2 : 1;
        g.fill(x, y, x + w, y + edge, light);
        g.fill(x, y, x + edge, y + h, light);
        g.fill(x + w - edge, y, x + w, y + h, 0xFF555555);
        g.fill(x, y + h - edge, x + w, y + h, 0xFF555555);
    }

    private void drawList(GuiGraphics g) {
        clip(g, x + 10, y + 10, x + leftW + 4, y + 8 + contentH());
        int top = y + 14 - scroll;
        for (int i = 0; i < factions.size(); i++) {
            Faction f = factions.get(i);
            int yy = top;
            top += rowHeight(f);
            if (top < y + 10 || yy > y + contentH()) continue;
            if (f.id == selected) drawText(g, new StyledText(">", ""), x + 14, yy, 8, rowHeight(f), 0xEEEEEE, 0, 10);
            int nameX = x + 23;
            drawText(g, name(f), nameX, yy, x + leftW + 2 - nameX, rowHeight(f) - 1, 0xC6C6C6, 0, size(f, "Name"));
        }
        g.disableScissor();
        int track = contentH() - 8, max = maxScroll();
        if (max > 0) {
            g.fill(x + leftW + 4, y + 12, x + leftW + 7, y + 12 + track, 0xFF505050);
            int thumb = Math.max(12, track * track / Math.max(track, totalRowsHeight()));
            int thumbY = y + 12 + scroll * (track - thumb) / max;
            g.fill(x + leftW + 4, thumbY, x + leftW + 7, thumbY + thumb, 0xFFAAAAAA);
        }
    }

    private void drawText(GuiGraphics g, StyledText text, int px, int py, int width, int height, int color, int skip, int size) {
        if (width <= 0 || height <= 0) return;
        float scale = size / 9f;
        List<StyledText.Line> lines = text.lines(width, c -> font.width(c) * scale);
        clip(g, px, py, px + width, py + height);
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().scale(scale, scale, 1);
        for (int i = Math.max(0, skip); i < lines.size(); i++) {
            int yy = (i - skip) * 11;
            if (yy * scale >= height) break;
            g.drawString(font, lines.get(i).component(), 0, yy, color, false);
        }
        g.pose().popPose();
        g.disableScissor();
    }

    private int barColor(Faction f) { return visual(f).contains("BarColor") ? visual(f).getInt("BarColor") & 0xFFFFFF : 0xC6C6C6; }
    private void drawStanding(GuiGraphics g, Faction f) {
        int bx = rightX(), bw = FactionLayout.BAR_WIDTH, by = barY(), bh = FactionLayout.BAR_HEIGHT;
        FactionStanding axis = FactionStanding.scale(f.neutralPoints, f.friendlyPoints);
        int color = barColor(f);
        g.fill(bx, by, bx + bw, by + bh, 0xFF202020);
        g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, 0xFF454545);
        int filled = (int)Math.round((bw - 2) * axis.fraction(currentPoints(f)));
        if (filled > 0) {
            g.fill(bx + 1, by + 1, bx + 1 + filled, by + bh - 1, color | 0xFF000000);
            int light = brighten(color, 40), dark = brighten(color, -45);
            g.fill(bx + 1, by + 1, bx + 1 + filled, by + 2, light | 0xFF000000);
            g.fill(bx + 1, by + bh - 2, bx + 1 + filled, by + bh - 1, dark | 0xFF000000);
        }
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        int heart = FactionLayout.HEART, hy = by + (bh - heart) / 2;
        int dark = bx + Math.max(0, Math.min(bw - heart, 1 + (int)Math.round((bw - 2) * axis.fraction(f.neutralPoints)) - heart / 2));
        int friendly = bx + Math.max(0, Math.min(bw - heart, 1 + (int)Math.round((bw - 2) * axis.fraction(f.friendlyPoints)) - heart / 2));
        g.blitSprite(HEART_CONTAINER, dark, hy, heart, heart);
        g.blitSprite(HEART_WITHERED, dark, hy, heart, heart);
        g.blitSprite(HEART_CONTAINER, friendly, hy, heart, heart);
        g.blitSprite(HEART_FULL, friendly, hy, heart, heart);
    }

    private static int brighten(int color, int amount) {
        int r = Math.max(0, Math.min(255, (color >> 16 & 255) + amount));
        int g = Math.max(0, Math.min(255, (color >> 8 & 255) + amount));
        int b = Math.max(0, Math.min(255, (color & 255) + amount));
        return r << 16 | g << 8 | b;
    }

    /** The outward 10 x 16 cape face at (1,1), scaled from the 64 x 32 atlas. */
    public static void drawFlag(GuiGraphics g, String flag, int x, int y, int w, int h) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF151515);
        ResourceLocation location = ResourceLocation.tryParse(flag);
        if (location == null) return;
        ResourceIndex.ensureFresh();
        TexturePreviewLoader.drain();
        var preview = TexturePreviewLoader.peek(location);
        if (preview == null || preview.failed()) return;
        int atlasW = preview.width(), atlasH = preview.height();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        g.blit(preview.front(), x, y, w, h, atlasW / 64f, atlasH / 32f,
            Math.max(1, atlasW * 10 / 64), Math.max(1, atlasH * 16 / 32), atlasW, atlasH);
    }

    private void drawTabs(GuiGraphics g) {
        for (int i = 0; i < 3; i++) {
            int tx = x + 8 + i * 34, ty = y - (i == 1 ? 30 : 26);
            frame(g, tx, ty, 30, i == 1 ? 32 : 26, i == 1 ? 0xFFC6C6C6 : 0xFF999999);
            g.renderItem((i == 0 ? Items.CRAFTING_TABLE : i == 1 ? Items.RED_BANNER : Items.WRITABLE_BOOK).getDefaultInstance(), tx + 7, ty + 6);
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (textPanel != null && editable()) {
            if (textPanel.mouseClicked(mx, my, button)) { setFocused(null); return true; }
            textPanel.blur();
        }
        mx /= uiScale;
        my /= uiScale;
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (!edit() && my >= y - 30 && my < y && mx >= x + 8 && mx < x + 110) {
            int tab = (int)(mx - x - 8) / 34;
            if (tab == 0) minecraft.setScreen(new InventoryScreen(minecraft.player));
            if (tab == 2) minecraft.setScreen(new GuiQuestLog(minecraft.player));
            return true;
        }
        if (maxScroll() > 0 && mx >= x + leftW + 4 && mx < x + leftW + 8 && my >= y + 10 && my < y + contentH()) {
            scrolling = true;
            dragScroll(my);
            return true;
        }
        if (mx >= x + 10 && mx < x + leftW + 4 && my >= y + 10 && my < y + contentH()) {
            int index = rowAt(my - y - 14 + scroll);
            if (index >= 0 && index < factions.size()) {
                Faction f = factions.get(index);
                selected = f.id;
                descriptionScroll = 0;
                if (editable()) { dragId = selected; pressY = my; moving = false; }
            }
            return true;
        }
        Faction f = current();
        if (editable() && f != null && mx >= rightX() && mx < x + w - 8) {
            if (my >= y + 12 && my < y + 16 + flagH() && mx < headerTextX()) chooseFlag(f);
            else if (my >= titleY() - 2 && my < relationY(f)) editText(f, "Title", f.name);
            else if (my >= relationY(f) && my < pointsY(f)) editText(f, relationKey(f), relationFallback(relationKey(f)));
            else if ((my >= pointsY(f) && my < pointsY(f) + lineHeight(f, "Points") + 2)
                    || (my >= barY() - 4 && my < barY() + 10)) editBar(f);
            else if (my >= descY() && my < barY() - 16) editText(f, "Description", "");
            else return super.mouseClicked(mx, my, button);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (textPanel != null && editable() && textPanel.mouseDragged(mx, my, button, dx, dy)) return true;
        mx /= uiScale;
        my /= uiScale;
        dx /= uiScale;
        dy /= uiScale;
        if (button == 0 && scrolling) { dragScroll(my); return true; }
        if (button == 0 && dragId >= 0) {
            if (Math.abs(my - pressY) > 4) moving = true;
            if (moving) {
                if (my < y + 22) scroll -= 6;
                if (my > y + contentH() - 12) scroll += 6;
                clampScroll();
                int from = -1;
                for (int i = 0; i < factions.size(); i++) if (factions.get(i).id == dragId) from = i;
                int to = Math.max(0, Math.min(factions.size() - 1, rowAt(my - y - 14 + scroll)));
                if (from >= 0 && from != to) {
                    Faction faction = factions.remove(from);
                    factions.add(to, faction);
                    for (int i = 0; i < factions.size(); i++) visual(factions.get(i)).putInt("Order", i);
                }
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (textPanel != null && textPanel.mouseReleased()) return true;
        mx /= uiScale;
        my /= uiScale;
        boolean handled = scrolling || dragId >= 0;
        boolean editName = button == 0 && dragId >= 0 && !moving && editable();
        scrolling = false;
        dragId = -1;
        moving = false;
        if (editName && current() != null) editName(current());
        return handled || super.mouseReleased(mx, my, button);
    }
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (textPanel != null && textPanel.mouseScrolled(mx, my, delta)) return true;
        mx /= uiScale;
        my /= uiScale;
        if (mx >= x + 8 && mx < x + leftW + 8 && my >= y && my < y + contentH()) {
            scroll -= (int)(delta * 16);
            clampScroll();
            return true;
        }
        Faction f = current();
        if (f != null && mx >= rightX() && mx < x + w && my >= descY() && my < barY() - 16) {
            float scale = size(f, "Description") / 9f;
            int count = text(f, "Description", "").lines(rightW(), c -> font.width(c) * scale).size();
            int visible = Math.max(1, (int)((barY() - descY() - 20) / (11 * scale)));
            descriptionScroll = Math.max(0, Math.min(count - visible, descriptionScroll - (int)(delta * 2)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }
    private int rowHeight(Faction f) { return lineHeight(f, "Name") + 3; }
    private int totalRowsHeight() { return factions.stream().mapToInt(this::rowHeight).sum(); }
    private int rowAt(double offset) {
        if (offset < 0) return 0;
        for (int i = 0; i < factions.size(); i++) {
            offset -= rowHeight(factions.get(i));
            if (offset < 0) return i;
        }
        return factions.size();
    }
    private int maxScroll() { return Math.max(0, totalRowsHeight() - contentH() + 14); }
    private void clampScroll() { scroll = Math.max(0, Math.min(maxScroll(), scroll)); }
    private void dragScroll(double my) {
        scroll = (int)((my - y - 12) / Math.max(1, contentH() - 8) * maxScroll());
        clampScroll();
    }

    private void chooseFlag(Faction f) {
        minecraft.setScreen(new GuiBetterMaterialEditor(this, parent.npc, visual(f).getString("Flag"),
            flag -> visual(f).putString("Flag", flag.toString())));
    }
    private void editText(Faction f, String key, String fallback) {
        openText(f, key, text(f, key, fallback), value -> {
            visual(f).putString(key, value.text());
            visual(f).putString(key + "Spans", value.spans());
        });
    }
    private void editName(Faction f) {
        openText(f, "Name", name(f), value -> {
            f.name = value.text();
            visual(f).putString("NameSpans", value.spans());
        });
    }
    private void openText(Faction f, String key, StyledText value, Consumer<StyledText> commit) {
        dragId = -1;
        setFocused(null);
        textPanel = new FactionTextPanel(font, key, value, size(f, key), commit,
            next -> { visual(f).putInt(key + "Size", next); clampScroll(); descriptionScroll = 0; },
            key.equals("Points") ? rgb -> visual(f).putInt("BarColor", rgb) : null, this::closeTextPanel);
        rebuildWidgets();
    }
    private void editBar(Faction f) {
        openText(f, "Points", new StyledText(Integer.toString(currentPoints(f)), ""), value -> {});
    }
    private void closeTextPanel() {
        textPanel = null;
        setFocused(null);
        rebuildWidgets();
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (textPanel != null && editable() && textPanel.keyPressed(key, scan, modifiers)) return true;
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char ch, int modifiers) {
        if (textPanel != null && editable() && textPanel.charTyped(ch, modifiers)) return true;
        return super.charTyped(ch, modifiers);
    }

    public void save() {
        if (!editable() || factions.isEmpty()) return;
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Faction f : factions) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", f.id);
            entry.putString("Name", f.name);
            entry.put(FactionVisual.KEY, visual(f).copy());
            list.add(entry);
        }
        tag.put("Factions", list);
        saving = true;
        saveButton.active = false;
        status = I18n.get("cnpclines.faction.saving");
        PacketDistributor.sendToServer(new FactionPackets.Save(tag));
    }
    @Override public void onClose() { if (!saving) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

}
