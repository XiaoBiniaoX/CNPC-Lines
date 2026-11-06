package top.cnpclines.faction.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import top.cnpcplus.text.StyledText;
import top.cnpcplus.text.client.StyledTextArea;
import top.cnpcplus.transport.TransportSpans;
import top.cnpcplus.transport.client.TextAreaSelectionAccess;

/** Transporter-style sidebar: the clicked element owns its content, size and selection styles. */
final class FactionTextPanel {
    private static final int[] COLORS = {
        0xFFFFFF, 0xE0E0E0, 0xCCCCCC, 0x999999, 0x666666, 0x333333, 0x000000, 0xFFD700,
        0xFF5555, 0xFF7F50, 0xFFAA00, 0xFFFF55, 0xAAFF55, 0x55FF55, 0x55FFFF, 0xFF55FF
    };
    private final Font font;
    private final String key;
    private final Consumer<StyledText> textCommit;
    private final IntConsumer sizeCommit, colorCommit;
    private final Runnable close;
    private final List<Button> buttons = new ArrayList<>();
    private StyledText draft;
    private StyledTextArea area;
    private EditBox sizeBox, hexBox;
    private int width, height, fontSize, paletteY, hexY, anchor;
    private boolean dragging;
    private String hint = "";

    FactionTextPanel(Font font, String key, StyledText text, int size, Consumer<StyledText> textCommit,
                     IntConsumer sizeCommit, IntConsumer colorCommit, Runnable close) {
        this.font = font;
        this.key = key;
        this.draft = text.legacy();
        this.fontSize = size;
        this.textCommit = textCommit;
        this.sizeCommit = sizeCommit;
        this.colorCommit = colorCommit;
        this.close = close;
    }

    static int widthFor(int screenWidth) { return Math.max(160, Math.min(220, screenWidth * 2 / 5)); }

    void init(int width, int height) {
        this.width = width;
        this.height = height;
        buttons.clear();
        buttons.add(Button.builder(CommonComponents.GUI_DONE, b -> close.run()).bounds(width - 48, 4, 44, 20).build());
        sizeBox = new EditBox(font, width - 102, 30, 46, 18, Component.translatable("cnpclines.faction.font_size"));
        sizeBox.setMaxLength(2);
        sizeBox.setFilter(value -> value.matches("[0-9]*"));
        sizeBox.setValue(Integer.toString(fontSize));
        sizeBox.setTooltip(Tooltip.create(Component.translatable("cnpclines.faction.font_range")));
        sizeBox.setResponder(value -> {
            if (!value.isEmpty()) {
                int size = Integer.parseInt(value);
                boolean valid = size >= 6 && size <= 24;
                sizeBox.setTextColor(valid ? 0xE0E0E0 : 0xFF5555);
                if (valid) { fontSize = size; sizeCommit.accept(size); }
            }
        });
        buttons.add(Button.builder(Component.literal("−"), b -> sizeBox.setValue(Integer.toString(Math.max(6, fontSize - 1))))
            .bounds(width - 52, 29, 22, 20).build());
        buttons.add(Button.builder(Component.literal("+"), b -> sizeBox.setValue(Integer.toString(Math.min(24, fontSize + 1))))
            .bounds(width - 28, 29, 22, 20).build());
        int contentH = Math.max(30, Math.min(96, height - 184));
        if (colorCommit == null) {
            area = new StyledTextArea(67, 6, 68, width - 12, contentH, draft, () -> draft);
            area.setListener(value -> { draft = draft.edit(value); textCommit.accept(draft); });
            int fmtY = 72 + contentH, bw = (width - 12) / 5;
            String[] labels = {"fmtBold", "fmtUnderline", "fmtItalic", "clearColor", "fmtReset"};
            for (int i = 0; i < labels.length; i++) {
                int format = i;
                buttons.add(Button.builder(Component.translatable("cnpcplus.transporter.editor." + labels[i]), b -> {
                    int flags = switch (format) { case 0 -> TransportSpans.FLAG_BOLD; case 1 -> TransportSpans.FLAG_UNDERLINE;
                        case 2 -> TransportSpans.FLAG_ITALIC; default -> 0; };
                    applyStyle(format == 3 ? TransportSpans.CLEAR_COLOR : -1, flags, format != 3);
                }).bounds(6 + i * bw, fmtY, bw - 2, 18).build());
            }
            paletteY = fmtY + 32;
        } else {
            area = null;
            paletteY = 100;
        }
        int cell = Math.min(22, (width - 12) / 8);
        for (int i = 0; i < COLORS.length; i++) {
            int color = COLORS[i];
            buttons.add(Button.builder(Component.empty(), b -> setColor(color))
                .bounds(6 + i % 8 * cell, paletteY + i / 8 * 18, cell - 2, 16).build());
        }
        hexY = paletteY + 40;
        hexBox = new EditBox(font, 6, hexY, width - 76, 18, Component.translatable("cnpclines.faction.hex_color"));
        hexBox.setMaxLength(6);
        hexBox.setFilter(value -> value.matches("[0-9a-fA-F]*"));
        hexBox.setValue("FFFFFF");
        buttons.add(Button.builder(Component.translatable("cnpcplus.transporter.editor.color"), b -> {
            if (hexBox.getValue().length() == 6) setColor(Integer.parseInt(hexBox.getValue(), 16));
        }).bounds(width - 66, hexY - 1, 60, 20).build());
        dragging = false;
    }

    private void setColor(int rgb) {
        hexBox.setValue(String.format("%06X", rgb));
        if (colorCommit != null) colorCommit.accept(rgb);
        else applyStyle(rgb, 0, false);
    }

    private void applyStyle(int rgb, int flags, boolean toggle) {
        int start = area.getSelectionBeforeText().length();
        int end = area.getText().length() - area.getSelectionAfterText().length();
        if (end <= start) { hint = "cnpclines.faction.select_text"; return; }
        draft = draft.apply(start, end, rgb, flags, toggle);
        textCommit.accept(draft);
        hint = "";
    }

    void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0xEB101018);
        g.fill(width - 1, 0, width, height, 0xFF505068);
        g.drawString(font, font.plainSubstrByWidth(I18n.get("cnpclines.faction.size." + key), width - 60), 6, 10, 0xFFD700, false);
        g.drawString(font, Component.translatable("cnpclines.faction.font_size"), 6, 35, 0xCCCCCC, false);
        var contentLabel = font.split(Component.translatable(colorCommit == null ? "cnpclines.faction.content" : "cnpclines.faction.reputation_readonly"), width - 12);
        for (int i = 0; i < Math.min(3, contentLabel.size()); i++) g.drawString(font, contentLabel.get(i), 6, 54 + i * 11, 0xCCCCCC, false);
        if (area != null) area.render(g, mx, my);
        g.drawString(font, Component.translatable(colorCommit == null ? "cnpclines.faction.selection_color" : "cnpcplus.transporter.editor.color"),
            6, paletteY - 12, 0x88CCFF, false);
        sizeBox.render(g, mx, my, partial);
        hexBox.render(g, mx, my, partial);
        for (Button button : buttons) button.render(g, mx, my, partial);
        int cell = Math.min(22, (width - 12) / 8);
        for (int i = 0; i < COLORS.length; i++) {
            int cx = 6 + i % 8 * cell, cy = paletteY + i / 8 * 18;
            g.fill(cx + 2, cy + 2, cx + cell - 4, cy + 14, 0xFF000000 | COLORS[i]);
        }
        if (!hint.isEmpty()) g.drawString(font, font.plainSubstrByWidth(I18n.get(hint), width - 12), 6, hexY + 26, 0xFFD700, false);
    }

    boolean contains(double mx) { return mx >= 0 && mx < width; }
    boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx)) return false;
        if (button != 0) return true;
        if (area != null && mx >= area.x && mx < area.x + area.width && my >= area.y && my < area.y + area.height) {
            sizeBox.setFocused(false);
            hexBox.setFocused(false);
            area.m_6375_(mx, my, button);
            anchor = ((TextAreaSelectionAccess)(Object)area).cnpcplus$selectionStart();
            area.clicked = false; // Drag selection is forwarded below, once per input event.
            dragging = true;
            return true;
        }
        // Palette/format clicks keep the native selection intact.
        for (Button control : buttons) if (control.mouseClicked(mx, my, button)) return true;
        boolean sizeHit = sizeBox.mouseClicked(mx, my, button), hexHit = hexBox.mouseClicked(mx, my, button);
        sizeBox.setFocused(sizeHit);
        hexBox.setFocused(hexHit);
        if (area != null) { area.active = false; area.clicked = false; }
        return true;
    }

    boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && dragging && area != null) {
            if (area.clickScrolling) return true; // Native render handles the scrollbar using the current mouse position.
            TextAreaSelectionAccess selection = (TextAreaSelectionAccess)(Object)area;
            int pos = selection.cnpcplus$selectionPos(mx, my);
            selection.cnpcplus$select(Math.min(anchor, pos), Math.max(anchor, pos), pos);
            return true;
        }
        return sizeBox.mouseDragged(mx, my, button, dx, dy) || hexBox.mouseDragged(mx, my, button, dx, dy);
    }

    boolean mouseReleased() {
        boolean handled = dragging;
        dragging = false;
        if (area != null) { area.clicked = false; area.clickScrolling = false; }
        return handled;
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        if (!contains(mx)) return false;
        if (area != null) area.m_6050_(mx, my, delta);
        return true;
    }
    void blur() {
        sizeBox.setFocused(false);
        hexBox.setFocused(false);
        if (area != null) { area.active = false; area.clicked = false; }
    }
    void tick() { if (area != null) area.tick(); }
    boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256) { close.run(); return true; }
        if (sizeBox.isFocused()) return sizeBox.keyPressed(key, scan, modifiers);
        if (hexBox.isFocused()) return hexBox.keyPressed(key, scan, modifiers);
        return area != null && area.active && area.m_7933_(key, scan, modifiers);
    }
    boolean charTyped(char ch, int modifiers) {
        if (sizeBox.isFocused()) return sizeBox.charTyped(ch, modifiers);
        if (hexBox.isFocused()) return hexBox.charTyped(ch, modifiers);
        return area != null && area.active && area.m_5534_(ch, modifiers);
    }
}
