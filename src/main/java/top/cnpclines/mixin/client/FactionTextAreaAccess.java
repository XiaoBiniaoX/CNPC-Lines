package top.cnpclines.mixin.client;

import noppes.npcs.client.gui.util.GuiTextArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Forward drag input once per event; tick the cursor without consuming the global wheel. */
@Mixin(value = GuiTextArea.class, remap = false)
public interface FactionTextAreaAccess {
    @Invoker("getSelectionPos") int cnpclines$selectionPos(int x, int y);
    @Accessor("cursorCounter") int cnpclines$cursorCounter();
    @Accessor("cursorCounter") void cnpclines$cursorCounter(int value);
}
