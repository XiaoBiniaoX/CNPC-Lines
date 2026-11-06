package top.cnpclines.faction;

import java.util.HashSet;
import java.util.Set;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import noppes.npcs.CustomItems;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.controllers.FactionController;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.controllers.data.PlayerData;
import top.cnpclines.network.NetworkHandler;

public final class FactionPackets {
    public static class Request implements IMessage {
        private boolean edit;
        public Request() { }
        public Request(boolean edit) { this.edit = edit; }
        @Override public void fromBytes(ByteBuf buf) { edit = buf.readBoolean(); }
        @Override public void toBytes(ByteBuf buf) { buf.writeBoolean(edit); }
        public static class Handler implements IMessageHandler<Request, IMessage> {
            @Override public IMessage onMessage(Request msg, MessageContext context) {
                EntityPlayerMP player = context.getServerHandler().player;
                player.getServerWorld().addScheduledTask(() -> reply(player, msg.edit && canEdit(player), "", false));
                return null;
            }
        }
    }
    public static class Save implements IMessage {
        private NBTTagCompound data;
        public Save() { }
        public Save(NBTTagCompound data) { this.data = data; }
        @Override public void fromBytes(ByteBuf buf) { data = ByteBufUtils.readTag(buf); }
        @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeTag(buf, data); }
        public static class Handler implements IMessageHandler<Save, IMessage> {
            @Override public IMessage onMessage(Save msg, MessageContext context) {
                EntityPlayerMP player = context.getServerHandler().player;
                player.getServerWorld().addScheduledTask(() -> {
                String error = canEdit(player) ? validate(msg.data) : "cnpclines.faction.denied";
                if (error.isEmpty()) {
                    NBTTagList edits = msg.data.getTagList("Factions", 10);
                    for (int i = 0; i < edits.tagCount(); i++) {
                        NBTTagCompound edit = edits.getCompoundTagAt(i);
                        Faction faction = FactionController.instance.getFaction(edit.getInteger("Slot"));
                        faction.name = edit.getString("Name");
                        ((FactionVisual)faction).cnpclines$visual(edit.getCompoundTag(FactionVisual.KEY));
                    }
                    // One native disk save; normal sync uses the same faction NBT extension.
                    FactionController.instance.saveFactions();
                    for (int i = 0; i < edits.tagCount(); i++) {
                        Faction faction = FactionController.instance.getFaction(edits.getCompoundTagAt(i).getInteger("Slot"));
                        noppes.npcs.Server.sendToAll(noppes.npcs.CustomNpcs.Server, noppes.npcs.constants.EnumPacketClient.SYNC_UPDATE,
                            1, faction.writeNBT(new NBTTagCompound()));
                    }
                }
                reply(player, canEdit(player), error, error.isEmpty());
                });
                return null;
            }
        }
    }
    public static class Snapshot implements IMessage {
        private NBTTagCompound data;
        public Snapshot() { }
        public Snapshot(NBTTagCompound data) { this.data = data; }
        @Override public void fromBytes(ByteBuf buf) { data = ByteBufUtils.readTag(buf); }
        @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeTag(buf, data); }
        public static class Handler implements IMessageHandler<Snapshot, IMessage> {
            @Override public IMessage onMessage(Snapshot msg, MessageContext context) {
                NetworkHandler.factionSnapshot.accept(msg.data);
                return null;
            }
        }
    }
    public static void register() {
        NetworkHandler.INSTANCE.registerMessage(Request.Handler.class, Request.class, 1, Side.SERVER);
        NetworkHandler.INSTANCE.registerMessage(Save.Handler.class, Save.class, 2, Side.SERVER);
        NetworkHandler.INSTANCE.registerMessage(Snapshot.Handler.class, Snapshot.class, 3, Side.CLIENT);
    }

    private static boolean canEdit(EntityPlayerMP player) {
        return player.getHeldItemMainhand().getItem() == CustomItems.wand
            && CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.GLOBAL_FACTION);
    }

    private static String validate(NBTTagCompound data) {
        if (data == null) return "cnpclines.faction.invalid";
        NBTTagList edits = data.getTagList("Factions", 10);
        if (edits.tagCount() == 0 || edits.tagCount() > FactionController.instance.factions.size())
            return "cnpclines.faction.invalid";
        Set<Integer> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < edits.tagCount(); i++) {
            NBTTagCompound edit = edits.getCompoundTagAt(i);
            String name = edit.getString("Name");
            if (!ids.add(edit.getInteger("Slot")) || FactionController.instance.getFaction(edit.getInteger("Slot")) == null
                    || name.codePoints().allMatch(Character::isWhitespace) || name.length() > 20 || name.contains("\n") || name.contains("\r")
                    || !names.add(name)) return "cnpclines.faction.invalid_name";
            NBTTagCompound visual = edit.getCompoundTag(FactionVisual.KEY);
            for (String key : visual.getKeySet()) {
                if (!new HashSet<String>(java.util.Arrays.asList("Title", "Description", "Friendly", "Neutral", "Hostile", "ListIcon", "Flag", "Order",
                        "BarColor", "TitleSpans", "DescriptionSpans", "FriendlySpans", "NeutralSpans",
                        "HostileSpans", "NameSpans", "ListIconSpans", "NameSize", "TitleSize", "DescriptionSize",
                        "FriendlySize", "NeutralSize", "HostileSize", "ListIconSize", "PointsSize")).contains(key))
                    return "cnpclines.faction.invalid";
                if (key.endsWith("Size") && (!visual.hasKey(key, 3) || visual.getInteger(key) < 6 || visual.getInteger(key) > 24))
                    return "cnpclines.faction.invalid";
                if (!key.equals("Order") && !key.equals("BarColor") && !key.endsWith("Size")
                        && visual.getString(key).length() > (key.endsWith("Spans") ? 32768 : 8192))
                    return "cnpclines.faction.invalid";
            }
            String flag = visual.getString("Flag");
            if (flag.length() > 320 || (!flag.isEmpty() && !flag.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+")))
                return "cnpclines.faction.invalid";
        }
        for (Faction existing : FactionController.instance.factions.values()) {
            if (!ids.contains(existing.id) && names.contains(existing.name)) return "cnpclines.faction.invalid_name";
        }
        return "";
    }

    private static void reply(EntityPlayerMP player, boolean edit, String error, boolean saved) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Edit", edit);
        tag.setBoolean("Saved", saved);
        tag.setString("Error", error);
        NBTTagList list = new NBTTagList();
        noppes.npcs.controllers.data.PlayerFactionData data = PlayerData.get(player).factionData;
        for (Faction faction : FactionController.instance.factions.values()) {
            if (!edit && (faction.hideFaction || !data.factionData.containsKey(faction.id))) continue;
            NBTTagCompound entry = faction.writeNBT(new NBTTagCompound());
            entry.setInteger("Points", data.factionData.getOrDefault(faction.id, faction.defaultPoints));
            list.appendTag(entry);
        }
        tag.setTag("Factions", list);
        NetworkHandler.INSTANCE.sendTo(new Snapshot(tag), player);
    }
}
