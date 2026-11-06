package top.cnpclines.faction.client;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.init.Items;
import noppes.npcs.client.Client;
import noppes.npcs.client.gui.global.GuiNPCManageFactions;
import noppes.npcs.client.gui.player.GuiQuestLog;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.constants.EnumPacketServer;
import bin.cnpcplus.text.StyledText;
import bin.cnpcplus.text.client.StyledTextRender;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import top.cnpclines.client.gui.GuiBetterMaterialEditor;
import top.cnpclines.client.gui.ResourceIndex;
import top.cnpclines.client.gui.TexturePreviewLoader;
import top.cnpclines.faction.*;
import top.cnpclines.network.NetworkHandler;

/** Player and editor share the same layout; editable drafts never change CNPC points. */
public final class FactionScreen extends GuiScreen {
    private static final ResourceLocation ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    private final GuiNPCManageFactions parent;
    private final Map<Integer, NBTTagCompound> drafts = new HashMap<>();
    private final Map<Integer, Integer> points = new HashMap<>();
    private final List<Faction> factions = new ArrayList<>();
    private int selected = -1, tick, x, y, w, h, leftW, scroll, descriptionScroll;
    private int dragId = -1;
    private boolean scrolling, moving, loaded, allowed, saving;
    private double pressY;
    private String status = "";
    private GuiButton saveButton;
    private float uiScale = 1;
    private FactionTextPanel textPanel;

    public FactionScreen(GuiNPCManageFactions parent, Faction selectedFaction) {
        this.parent = parent;
        if (selectedFaction != null) this.selected = selectedFaction.id;
    }

    private boolean edit() { return parent != null; }
    private boolean editable() { return edit() && allowed && !saving; }

    @Override public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        int sidebar = textPanel == null ? 0 : FactionTextPanel.widthFor(width) + 8;
        FactionLayout layout = FactionLayout.fit(width - sidebar, height, edit());
        uiScale = layout.scale();
        w = FactionLayout.WIDTH;
        h = FactionLayout.HEIGHT;
        x = layout.x() + (int)Math.ceil(sidebar / uiScale);
        y = layout.y();
        leftW = FactionLayout.LEFT;
        if (edit()) {
            saveButton = new GuiButton(10, x + 58, y + h + 16, 74, 20, I18n.format("cnpclines.common.save"));
            buttonList.add(saveButton);
            buttonList.add(new GuiButton(11, x + 136, y + h + 16, 74, 20, I18n.format("cnpclines.common.cancel")));
            saveButton.enabled = editable();
        }
        if (textPanel != null) textPanel.init(sidebar - 8, height);
        request();
    }

    private void request() { NetworkHandler.INSTANCE.sendToServer(new FactionPackets.Request(edit())); }
    @Override public void updateScreen() {
        if (textPanel != null) textPanel.tick();
        if (++tick % 20 == 0 && !saving) request();
    }

    public void receive(NBTTagCompound snapshot) {
        String error = snapshot.getString("Error");
        if (!error.isEmpty()) { status = I18n.format(error); saving = false; }
        allowed = snapshot.getBoolean("Edit");
        if (edit() && !allowed) status = I18n.format("cnpclines.faction.denied");
        NBTTagList list = snapshot.getTagList("Factions", 10);
        points.clear();
        if (!edit() || !loaded || snapshot.getBoolean("Saved")) {
            factions.clear();
            drafts.clear();
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                Faction faction = new Faction();
                faction.readNBT(tag);
                factions.add(faction);
                NBTTagCompound draft = tag.getCompoundTag(FactionVisual.KEY).copy();
                draft.removeTag("ListIcon");
                draft.removeTag("ListIconSpans");
                draft.removeTag("ListIconSize");
                drafts.put(faction.id, draft);
            }
            factions.sort(Comparator.comparingInt((Faction f) -> visual(f).hasKey("Order")
                ? visual(f).getInteger("Order") : f.id).thenComparingInt(f -> f.id));
            loaded = true;
        }
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            points.put(tag.getInteger("Slot"), tag.getInteger("Points"));
        }
        if (current() == null) selected = factions.isEmpty() ? -1 : factions.get(0).id;
        clampScroll();
        if (saveButton != null) saveButton.enabled = editable() && !factions.isEmpty();
        if (saving && snapshot.getBoolean("Saved")) {
            saving = false;
            Faction chosen = current();
            if (chosen != null) {
                ((FactionVisual)chosen).cnpclines$visual(visual(chosen));
                parent.setGuiData(chosen.writeNBT(new NBTTagCompound()));
            }
            mc.displayGuiScreen(parent);
            Client.sendData(EnumPacketServer.FactionsGet);
        }
    }

    private Faction current() { return factions.stream().filter(f -> f.id == selected).findFirst().orElse(null); }
    private NBTTagCompound visual(Faction faction) { return drafts.get(faction.id); }
    private int currentPoints(Faction f) { return points.getOrDefault(f.id, f.defaultPoints); }
    private int relation(Faction f) { return FactionStanding.relation(currentPoints(f), f.neutralPoints, f.friendlyPoints); }
    private String relationKey(Faction f) { return relation(f) < 0 ? "Hostile" : relation(f) == 0 ? "Neutral" : "Friendly"; }
    private int relationColor(Faction f) { return relation(f) < 0 ? 0xFF5555 : relation(f) == 0 ? 0xFFFF55 : 0x55FF55; }
    private StyledText text(Faction f, String key, String fallback) {
        NBTTagCompound v = visual(f);
        return new StyledText(v.hasKey(key) ? v.getString(key) : fallback, v.getString(key + "Spans")).legacy();
    }
    private StyledText name(Faction f) { return new StyledText(f.name, visual(f).getString("NameSpans")).legacy(); }
    private String relationFallback(String key) {
        if (key.equals("Friendly")) return I18n.format("faction.friendly");
        if (key.equals("Hostile")) return I18n.format("faction.unfriendly");
        return I18n.format("faction.neutral");
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
        return visual(f).hasKey(key + "Size") ? Math.max(6, Math.min(24, visual(f).getInteger(key + "Size")))
            : key.equals("Title") || key.equals("Points") ? 11 : 10;
    }

    private void clip(int x1, int y1, int x2, int y2) {
        scissor((int)(x1 * uiScale), (int)(y1 * uiScale), (int)Math.ceil(x2 * uiScale), (int)Math.ceil(y2 * uiScale));
    }
    public static void scissor(int x1, int y1, int x2, int y2) {
        Minecraft mc = Minecraft.getMinecraft();
        int factor = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x1 * factor, mc.displayHeight - y2 * factor, Math.max(0, x2 - x1) * factor, Math.max(0, y2 - y1) * factor);
    }

    @Override public void drawScreen(int mx, int my, float partial) {
        drawDefaultBackground();
        int actualMouseX = mx, actualMouseY = my;
        mx = (int)(mx / uiScale);
        my = (int)(my / uiScale);
        GlStateManager.pushMatrix();
        GlStateManager.scale(uiScale, uiScale, 1);
        if (!edit()) drawTabs();
        frame(x, y, w, h, 0xFFC6C6C6);
        frame(x + 8, y + 8, leftW, contentH(), 0xFF686868);
        frame(rightX() - 6, y + 8, FactionLayout.RIGHT, contentH(), 0xFF686868);
        drawList();
        Faction f = current();
        if (f == null) drawCenteredString(fontRenderer, I18n.format(loaded ? "faction.nostanding" : "cnpclines.faction.loading"), x + w / 2, y + 40, 0xC6C6C6);
        else {
            String flag = visual(f).getString("Flag");
            drawFlag(flag, rightX(), y + 14, flagW(), flagH());
            if (edit() && flag.isEmpty()) drawText(new StyledText(I18n.format("cnpclines.faction.empty_flag"), ""),
                rightX() + 2, y + 23, flagW() - 4, flagH() - 12, 0xAAAAAA, 0, 8);
            drawText(editableText(f, "Title", f.name), headerTextX(), titleY(),
                rightX() + rightW() - headerTextX(), lineHeight(f, "Title"), 0xC6C6C6, 0, size(f, "Title"));
            drawText(text(f, relationKey(f), relationFallback(relationKey(f))), headerTextX(), relationY(f),
                rightX() + rightW() - headerTextX(), lineHeight(f, relationKey(f)), relationColor(f), 0, size(f, relationKey(f)));
            drawText(new StyledText(Integer.toString(currentPoints(f)), ""), headerTextX(), pointsY(f),
                rightX() + rightW() - headerTextX(), lineHeight(f, "Points"), barColor(f), 0, size(f, "Points"));
            Gui.drawRect(rightX(), descY() - 7, rightX() + rightW(), descY() - 6, 0xFF505050);
            drawText(editableText(f, "Description", ""), rightX(), descY(),
                rightW(), barY() - descY() - 20, 0xBBBBBB, descriptionScroll, size(f, "Description"));
            Gui.drawRect(rightX(), barY() - 12, rightX() + rightW(), barY() - 11, 0xFF505050);
            drawStanding(f);
        }
        if (edit()) {
            frame(x, y + h, w, 40, 0xFFC6C6C6);
            String hint = status.isEmpty() ? I18n.format("cnpclines.faction.edit_hint") : status;
            fontRenderer.drawString( fontRenderer.trimStringToWidth(hint, w - 12), x + 6, y + h + 5,
                status.isEmpty() ? 0x404040 : 0xAA2222, false);
        }
        super.drawScreen(mx, my, partial);
        GlStateManager.popMatrix();
        if (textPanel != null) textPanel.render(actualMouseX, actualMouseY, partial);
    }

    private StyledText editableText(Faction f, String key, String fallback) {
        StyledText value = text(f, key, fallback);
        return edit() && value.text.isEmpty()
            ? new StyledText(I18n.format(key.equals("Title") ? "cnpclines.faction.empty_title" : "cnpclines.faction.empty_text"), "") : value;
    }

    private static void frame(int x, int y, int w, int h, int color) {
        Gui.drawRect(x, y, x + w, y + h, color);
        int light = color == 0xFFC6C6C6 ? 0xFFFFFFFF : 0xFF8B8B8B;
        int edge = color == 0xFFC6C6C6 ? 2 : 1;
        Gui.drawRect(x, y, x + w, y + edge, light);
        Gui.drawRect(x, y, x + edge, y + h, light);
        Gui.drawRect(x + w - edge, y, x + w, y + h, 0xFF555555);
        Gui.drawRect(x, y + h - edge, x + w, y + h, 0xFF555555);
    }

    private void drawList() {
        clip(x + 10, y + 10, x + leftW + 4, y + 8 + contentH());
        int top = y + 14 - scroll;
        for (int i = 0; i < factions.size(); i++) {
            Faction f = factions.get(i);
            int yy = top;
            top += rowHeight(f);
            if (top < y + 10 || yy > y + contentH()) continue;
            if (f.id == selected) drawText(new StyledText(">", ""), x + 14, yy, 8, rowHeight(f), 0xEEEEEE, 0, 10);
            int nameX = x + 23;
            drawText(name(f), nameX, yy, x + leftW + 2 - nameX, rowHeight(f) - 1, 0xC6C6C6, 0, size(f, "Name"));
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        int track = contentH() - 8, max = maxScroll();
        if (max > 0) {
            Gui.drawRect(x + leftW + 4, y + 12, x + leftW + 7, y + 12 + track, 0xFF505050);
            int thumb = Math.max(12, track * track / Math.max(track, totalRowsHeight()));
            int thumbY = y + 12 + scroll * (track - thumb) / max;
            Gui.drawRect(x + leftW + 4, thumbY, x + leftW + 7, thumbY + thumb, 0xFFAAAAAA);
        }
    }

    private void drawText(StyledText text, int px, int py, int width, int height, int color, int skip, int size) {
        if (width <= 0 || height <= 0) return;
        float scale = size / 9f;
        List<StyledText.Row> rows = text.rows(width, c -> Math.round(fontRenderer.getStringWidth(c) * scale));
        clip(px, py, px + width, py + height);
        GlStateManager.pushMatrix();
        GlStateManager.translate(px, py, 0);
        GlStateManager.scale(scale, scale, 1);
        for (int i = Math.max(0, skip); i < rows.size(); i++) {
            int yy = (i - skip) * 11;
            if (yy * scale >= height) break;
            StyledText.Row row = rows.get(i);
            StyledTextRender.draw(fontRenderer, text, row.start, row.end, 0, yy, 11, color);
        }
        GlStateManager.popMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private int barColor(Faction f) { return visual(f).hasKey("BarColor") ? visual(f).getInteger("BarColor") & 0xFFFFFF : 0xC6C6C6; }
    private void drawStanding(Faction f) {
        int bx = rightX(), bw = FactionLayout.BAR_WIDTH, by = barY(), bh = FactionLayout.BAR_HEIGHT;
        FactionStanding axis = FactionStanding.scale(f.neutralPoints, f.friendlyPoints);
        int color = barColor(f);
        Gui.drawRect(bx, by, bx + bw, by + bh, 0xFF202020);
        Gui.drawRect(bx + 1, by + 1, bx + bw - 1, by + bh - 1, 0xFF454545);
        int filled = (int)Math.round((bw - 2) * axis.fraction(currentPoints(f)));
        if (filled > 0) {
            Gui.drawRect(bx + 1, by + 1, bx + 1 + filled, by + bh - 1, color | 0xFF000000);
            int light = brighten(color, 40), dark = brighten(color, -45);
            Gui.drawRect(bx + 1, by + 1, bx + 1 + filled, by + 2, light | 0xFF000000);
            Gui.drawRect(bx + 1, by + bh - 2, bx + 1 + filled, by + bh - 1, dark | 0xFF000000);
        }
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        int heart = FactionLayout.HEART, hy = by + (bh - heart) / 2;
        int dark = bx + Math.max(0, Math.min(bw - heart, 1 + (int)Math.round((bw - 2) * axis.fraction(f.neutralPoints)) - heart / 2));
        int friendly = bx + Math.max(0, Math.min(bw - heart, 1 + (int)Math.round((bw - 2) * axis.fraction(f.friendlyPoints)) - heart / 2));
        blit(ICONS, dark, hy, heart, heart, 16, 0, 9, 9, 256, 256);
        blit(ICONS, dark, hy, heart, heart, 124, 0, 9, 9, 256, 256);
        blit(ICONS, friendly, hy, heart, heart, 16, 0, 9, 9, 256, 256);
        blit(ICONS, friendly, hy, heart, heart, 52, 0, 9, 9, 256, 256);
    }

    private static int brighten(int color, int amount) {
        int r = Math.max(0, Math.min(255, (color >> 16 & 255) + amount));
        int g = Math.max(0, Math.min(255, (color >> 8 & 255) + amount));
        int b = Math.max(0, Math.min(255, (color & 255) + amount));
        return r << 16 | g << 8 | b;
    }

    /** The outward 10 x 16 cape face at (1,1), scaled from the 64 x 32 atlas. */
    public static void drawFlag(String flag, int x, int y, int w, int h) {
        Gui.drawRect(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF151515);
        ResourceLocation location = location(flag);
        if (location == null) return;
        ResourceIndex.ensureFresh();
        TexturePreviewLoader.drain();
        TexturePreviewLoader.Preview preview = TexturePreviewLoader.peek(location);
        if (preview == null || preview.failed()) return;
        int atlasW = preview.width(), atlasH = preview.height();
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        blit(preview.front(), x, y, w, h, atlasW / 64f, atlasH / 32f,
            Math.max(1, atlasW * 10 / 64), Math.max(1, atlasH * 16 / 32), atlasW, atlasH);
    }

    private void drawTabs() {
        for (int i = 0; i < 3; i++) {
            int tx = x + 8 + i * 34, ty = y - (i == 1 ? 30 : 26);
            frame(tx, ty, 30, i == 1 ? 32 : 26, i == 1 ? 0xFFC6C6C6 : 0xFF999999);
            mc.getRenderItem().renderItemAndEffectIntoGUI(i == 0 ? new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.CRAFTING_TABLE)
                : new net.minecraft.item.ItemStack(i == 1 ? Items.BANNER : Items.WRITABLE_BOOK), tx + 7, ty + 6);
        }
    }

    private boolean click(double mx, double my, int button) {
        if (textPanel != null && editable()) {
            if (textPanel.mouseClicked(mx, my, button)) {  return true; }
            textPanel.blur();
        }
        mx /= uiScale;
        my /= uiScale;
        if (button != 0) return false;
        if (!edit() && my >= y - 30 && my < y && mx >= x + 8 && mx < x + 110) {
            int tab = (int)(mx - x - 8) / 34;
            if (tab == 0) mc.displayGuiScreen(new GuiInventory(mc.player));
            if (tab == 2) mc.displayGuiScreen(new GuiQuestLog(mc.player));
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
            else return false;
            return true;
        }
        return false;
    }

    private boolean drag(double mx, double my, int button, double dx, double dy) {
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
                    for (int i = 0; i < factions.size(); i++) visual(factions.get(i)).setInteger("Order", i);
                }
            }
            return true;
        }
        return false;
    }
    private boolean release(double mx, double my, int button) {
        if (textPanel != null && textPanel.mouseReleased()) return true;
        mx /= uiScale;
        my /= uiScale;
        boolean handled = scrolling || dragId >= 0;
        boolean editName = button == 0 && dragId >= 0 && !moving && editable();
        scrolling = false;
        dragId = -1;
        moving = false;
        if (editName && current() != null) editName(current());
        return handled || false;
    }
    private boolean scrollWheel(double mx, double my, double delta) {
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
            int count = text(f, "Description", "").rows(rightW(), c -> Math.round(fontRenderer.getStringWidth(c) * scale)).size();
            int visible = Math.max(1, (int)((barY() - descY() - 20) / (11 * scale)));
            descriptionScroll = Math.max(0, Math.min(count - visible, descriptionScroll - (int)(delta * 2)));
            return true;
        }
        return false;
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
        mc.displayGuiScreen(new GuiBetterMaterialEditor(this, parent.npc, visual(f).getString("Flag"),
            flag -> visual(f).setString("Flag", flag.toString())));
    }
    private void editText(Faction f, String key, String fallback) {
        openText(f, key, text(f, key, fallback), value -> {
            visual(f).setString(key, value.text);
            visual(f).setString(key + "Spans", value.spans);
        });
    }
    private void editName(Faction f) {
        openText(f, "Name", name(f), value -> {
            f.name = value.text;
            visual(f).setString("NameSpans", value.spans);
        });
    }
    private void openText(Faction f, String key, StyledText value, Consumer<StyledText> commit) {
        dragId = -1;
        
        textPanel = new FactionTextPanel(fontRenderer, key, value, size(f, key), commit,
            next -> { visual(f).setInteger(key + "Size", next); clampScroll(); descriptionScroll = 0; },
            key.equals("Points") ? rgb -> visual(f).setInteger("BarColor", rgb) : null, this::closeTextPanel);
        initGui();
    }
    private void editBar(Faction f) {
        openText(f, "Points", new StyledText(Integer.toString(currentPoints(f)), ""), value -> {});
    }
    private void closeTextPanel() {
        textPanel = null;
        
        initGui();
    }
    @Override protected void keyTyped(char ch, int key) throws IOException {
        if (textPanel != null && editable() && textPanel.keyTyped(ch, key)) return;
        if (key == Keyboard.KEY_ESCAPE) { close(); return; }
        super.keyTyped(ch, key);
    }
    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (!click(mx, my, button)) super.mouseClicked((int)(mx / uiScale), (int)(my / uiScale), button);
    }
    @Override protected void mouseClickMove(int mx, int my, int button, long elapsed) {
        if (!drag(mx, my, button, 0, 0)) super.mouseClickMove((int)(mx / uiScale), (int)(my / uiScale), button, elapsed);
    }
    @Override protected void mouseReleased(int mx, int my, int button) {
        if (!release(mx, my, button)) super.mouseReleased((int)(mx / uiScale), (int)(my / uiScale), button);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scrollWheel(Mouse.getEventX() * width / mc.displayWidth,
            height - Mouse.getEventY() * height / mc.displayHeight - 1, wheel > 0 ? 1 : -1);
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 10) save();
        if (button.id == 11) close();
    }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    public void save() {
        if (!editable() || factions.isEmpty()) return;
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (Faction f : factions) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("Slot", f.id);
            entry.setString("Name", f.name);
            entry.setTag(FactionVisual.KEY, visual(f).copy());
            list.appendTag(entry);
        }
        tag.setTag("Factions", list);
        saving = true;
        saveButton.enabled = false;
        status = I18n.format("cnpclines.faction.saving");
        NetworkHandler.INSTANCE.sendToServer(new FactionPackets.Save(tag));
    }
    private void close() { if (!saving) mc.displayGuiScreen(parent); }
    @Override public boolean doesGuiPauseGame() { return false; }

    private static void blit(ResourceLocation texture, int x, int y, int w, int h, float u, float v, int tw, int th, int aw, int ah) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        Gui.drawScaledCustomSizeModalRect(x, y, u, v, tw, th, w, h, aw, ah);
    }
    private static ResourceLocation location(String value) {
        if (value == null || value.isEmpty()) return null;
        if (!value.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+")) return null;
        return new ResourceLocation(value);
    }

}
