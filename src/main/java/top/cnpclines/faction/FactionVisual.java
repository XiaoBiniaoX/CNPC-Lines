package top.cnpclines.faction;

import net.minecraft.nbt.CompoundTag;

/** Attached to CNPC factions, so native save, load and sync retain the presentation. */
public interface FactionVisual {
    String KEY = "CNPCLinesVisual";
    CompoundTag cnpclines$visual();
    void cnpclines$visual(CompoundTag value);
}
