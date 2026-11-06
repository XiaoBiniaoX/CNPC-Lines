package top.cnpclines.faction.client;

import bin.cnpcplus.mixin.script.IGuiTextAreaScriptAccess;
import bin.cnpcplus.text.StyledText;
import bin.cnpcplus.text.client.StyledTextArea;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import top.cnpclines.mixin.client.FactionTextAreaAccess;

/** The clicked element owns its content, size and selection styles, on the same screen. */
final class FactionTextPanel {
    private static final int[] COLORS = {0xFFFFFF,0xE0E0E0,0xCCCCCC,0x999999,0x666666,0x333333,0x000000,0xFFD700,
        0xFF5555,0xFF7F50,0xFFAA00,0xFFFF55,0xAAFF55,0x55FF55,0x55FFFF,0xFF55FF};
    private final FontRenderer font;
    private final String key;
    private final Consumer<StyledText> textCommit;
    private final IntConsumer sizeCommit, colorCommit;
    private final Runnable close;
    private final List<GuiButton> buttons = new ArrayList<>();
    private StyledText draft;
    private StyledTextArea area;
    private GuiTextField sizeBox, hexBox;
    private int width, height, fontSize, paletteY, hexY, anchor;
    private boolean dragging;
    private String hint = "";

    FactionTextPanel(FontRenderer font, String key, StyledText text, int size, Consumer<StyledText> textCommit,
            IntConsumer sizeCommit, IntConsumer colorCommit, Runnable close) {
        this.font = font; this.key = key; this.draft = text.legacy(); this.fontSize = size;
        this.textCommit = textCommit; this.sizeCommit = sizeCommit; this.colorCommit = colorCommit; this.close = close;
    }
    static int widthFor(int screenWidth) { return Math.max(160, Math.min(220, screenWidth * 2 / 5)); }
    private void button(int id, int x, int y, int w, int h, String label) {
        buttons.add(new GuiButton(id, x, y, w, h, label));
    }
    void init(int width, int height) {
        syncText(); this.width = width; this.height = height; buttons.clear();
        button(9, width - 25, 4, 20, 20, "×");
        sizeBox = new GuiTextField(20, font, width - 102, 30, 46, 18);
        sizeBox.setMaxStringLength(2); sizeBox.setValidator(value -> value.matches("[0-9]*"));
        sizeBox.setText(Integer.toString(fontSize));
        button(21, width - 52, 29, 22, 20, "−"); button(22, width - 28, 29, 22, 20, "+");
        int contentH = Math.max(30, Math.min(96, height - 184));
        if (colorCommit == null) {
            area = new StyledTextArea(67, 6, 68, width - 12, contentH, draft);
            int fmtY = 72 + contentH, bw = (width - 12) / 5;
            String[] labels = {"bold", "underline", "italic", "clear", "reset"};
            for (int i = 0; i < labels.length; i++) button(200 + i, 6 + i * bw, fmtY, bw - 2, 18,
                I18n.format("cnpclines.faction.format." + labels[i]));
            paletteY = fmtY + 32;
        } else { area = null; paletteY = 100; }
        int cell = Math.min(22, (width - 12) / 8);
        for (int i = 0; i < COLORS.length; i++) button(100 + i, 6 + i % 8 * cell, paletteY + i / 8 * 18, cell - 2, 16, "");
        hexY = paletteY + 40;
        hexBox = new GuiTextField(30, font, 6, hexY, width - 76, 18);
        hexBox.setMaxStringLength(6); hexBox.setValidator(value -> value.matches("[0-9a-fA-F]*"));
        hexBox.setText("FFFFFF");
        button(31, width - 66, hexY - 1, 60, 20, I18n.format("cnpclines.faction.format.color"));
        dragging = false;
    }
    private void syncText() {
        if (area == null) return;
        StyledText value = area.value();
        if (!value.text.equals(draft.text) || !value.spans.equals(draft.spans)) {
            draft = value; textCommit.accept(draft);
        }
    }
    private void syncSize() {
        String value = sizeBox.getText();
        int size = value.isEmpty() ? 0 : Integer.parseInt(value);
        boolean valid = size >= 6 && size <= 24;
        sizeBox.setTextColor(valid ? 0xE0E0E0 : 0xFF5555);
        if (valid && size != fontSize) { fontSize = size; sizeCommit.accept(size); }
    }
    private void color(int rgb) {
        hexBox.setText(String.format("%06X", rgb));
        if (colorCommit != null) colorCommit.accept(rgb); else style(rgb, 0, false);
    }
    private void style(int rgb, int flags, boolean reset) {
        syncText();
        int start = area.getSelectionBeforeText().length();
        int end = area.getText().length() - area.getSelectionAfterText().length();
        if (end <= start) { hint = "cnpclines.faction.select_text"; return; }
        area.format(rgb, flags, reset); syncText(); hint = "";
    }
    void render(int mx, int my, float partial) {
        Gui.drawRect(0, 0, width, height, 0xEB101018);
        Gui.drawRect(width - 1, 0, width, height, 0xFF505068);
        font.drawString(font.trimStringToWidth(I18n.format("cnpclines.faction.size." + key), width - 60), 6, 10, 0xFFD700);
        font.drawString(I18n.format("cnpclines.faction.font_size"), 6, 35, 0xCCCCCC);
        List<String> content = font.listFormattedStringToWidth(I18n.format(colorCommit == null
            ? "cnpclines.faction.content" : "cnpclines.faction.reputation_readonly"), width - 12);
        for (int i = 0; i < Math.min(3, content.size()); i++) font.drawString(content.get(i), 6, 54 + i * 11, 0xCCCCCC);
        if (area != null) area.drawScreen(mx, my);
        font.drawString(I18n.format(colorCommit == null ? "cnpclines.faction.selection_color" : "cnpclines.faction.format.color"), 6, paletteY - 12, 0x88CCFF);
        sizeBox.drawTextBox(); hexBox.drawTextBox();
        for (GuiButton button : buttons) button.drawButton(Minecraft.getMinecraft(), mx, my, partial);
        int cell = Math.min(22, (width - 12) / 8);
        for (int i = 0; i < COLORS.length; i++) {
            int cx = 6 + i % 8 * cell, cy = paletteY + i / 8 * 18;
            Gui.drawRect(cx + 2, cy + 2, cx + cell - 4, cy + 14, 0xFF000000 | COLORS[i]);
        }
        if (!hint.isEmpty()) font.drawString(font.trimStringToWidth(I18n.format(hint), width - 12), 6, hexY + 26, 0xFFD700);
    }
    private boolean contains(double mx) { return mx >= 0 && mx < width; }
    boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx)) return false;
        if (button != 0) return true;
        int x = (int)mx, y = (int)my;
        if (area != null && x >= area.x && x < area.x + area.width && y >= area.y && y < area.y + area.height) {
            sizeBox.setFocused(false); hexBox.setFocused(false); area.mouseClicked(x, y, button);
            anchor = area.getSelectionBeforeText().length(); area.clicked = false; dragging = true; return true;
        }
        for (GuiButton control : buttons) if (control.mousePressed(Minecraft.getMinecraft(), x, y)) {
            control.playPressSound(Minecraft.getMinecraft().getSoundHandler());
            int id = control.id;
            if (id == 9) close.run();
            else if (id == 21 || id == 22) { sizeBox.setText(Integer.toString(Math.max(6, Math.min(24, fontSize + (id == 21 ? -1 : 1))))); syncSize(); }
            else if (id == 31 && hexBox.getText().length() == 6) color(Integer.parseInt(hexBox.getText(), 16));
            else if (id >= 100 && id < 116) color(COLORS[id - 100]);
            else if (id >= 200 && id <= 204) style(id == 203 ? -2 : -1, id == 200 ? 1 : id == 201 ? 2 : id == 202 ? 4 : 0, id == 204);
            return true;
        }
        boolean sizeHit = sizeBox.mouseClicked(x, y, button), hexHit = hexBox.mouseClicked(x, y, button);
        sizeBox.setFocused(sizeHit); hexBox.setFocused(hexHit);
        if (area != null) { area.active = false; area.clicked = false; }
        return true;
    }
    boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0 || !dragging || area == null) return false;
        if (area.clickScrolling) return true;
        int pos = ((FactionTextAreaAccess)(Object)area).cnpclines$selectionPos((int)mx, (int)my);
        IGuiTextAreaScriptAccess access = (IGuiTextAreaScriptAccess)(Object)area;
        access.cnpcplus$selectionStart(Math.min(anchor, pos)); access.cnpcplus$selectionEnd(Math.max(anchor, pos)); access.cnpcplus$cursorPosition(pos);
        return true;
    }
    boolean mouseReleased() {
        boolean handled = dragging; dragging = false;
        if (area != null) { area.clicked = false; area.clickScrolling = false; }
        return handled;
    }
    boolean mouseScrolled(double mx, double my, double delta) {
        if (!contains(mx)) return false;
        if (area != null) {
            IGuiTextAreaScriptAccess access = (IGuiTextAreaScriptAccess)(Object)area;
            access.cnpcplus$scroll(Math.max(0, Math.min(access.cnpcplus$container().linesCount - access.cnpcplus$container().visibleLines,
                access.cnpcplus$scroll() - (int)delta)));
        }
        return true;
    }
    void blur() { sizeBox.setFocused(false); hexBox.setFocused(false); if (area != null) { area.active = false; area.clicked = false; } }
    void tick() {
        sizeBox.updateCursorCounter(); hexBox.updateCursorCounter();
        if (area != null) {
            FactionTextAreaAccess access = (FactionTextAreaAccess)(Object)area;
            access.cnpclines$cursorCounter(access.cnpclines$cursorCounter() + 1); syncText();
        }
    }
    boolean keyTyped(char ch, int key) {
        if (key == 1) { close.run(); return true; }
        if (sizeBox.isFocused()) { sizeBox.textboxKeyTyped(ch, key); syncSize(); return true; }
        if (hexBox.isFocused()) { hexBox.textboxKeyTyped(ch, key); return true; }
        if (area != null && area.active) { area.keyTyped(ch, key); syncText(); return true; }
        return false;
    }
}
