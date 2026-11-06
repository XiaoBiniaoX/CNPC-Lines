package top.cnpclines.faction;

import net.minecraft.nbt.NBTTagCompound;

/** Attached to CNPC factions, so native save, load and sync retain the presentation. */
public interface FactionVisual {
    String KEY = "CNPCLinesVisual";
    NBTTagCompound cnpclines$visual();
    void cnpclines$visual(NBTTagCompound value);
}
