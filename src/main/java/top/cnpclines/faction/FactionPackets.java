package top.cnpclines.faction;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import noppes.npcs.CustomItems;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.controllers.FactionController;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.controllers.data.PlayerData;
import top.cnpclines.network.NetworkHandler;

public final class FactionPackets {
    public record Request(boolean edit) {
        public static void encode(Request msg, FriendlyByteBuf buf) { buf.writeBoolean(msg.edit); }
        public static Request decode(FriendlyByteBuf buf) { return new Request(buf.readBoolean()); }
        public static void handle(Request msg, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) reply(player, msg.edit && canEdit(player), "", false);
            context.get().setPacketHandled(true);
        }
    }

    public record Save(CompoundTag data) {
        public static void encode(Save msg, FriendlyByteBuf buf) { buf.writeNbt(msg.data); }
        public static Save decode(FriendlyByteBuf buf) { return new Save(buf.readNbt()); }
        public static void handle(Save msg, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                String error = canEdit(player) ? validate(msg.data) : "cnpclines.faction.denied";
                if (error.isEmpty()) {
                    ListTag edits = msg.data.getList("Factions", 10);
                    for (int i = 0; i < edits.size(); i++) {
                        CompoundTag edit = edits.getCompound(i);
                        Faction faction = FactionController.instance.getFaction(edit.getInt("Slot"));
                        faction.name = edit.getString("Name");
                        ((FactionVisual)faction).cnpclines$visual(edit.getCompound(FactionVisual.KEY));
                    }
                    // One native disk save; normal sync uses the same faction NBT extension.
                    FactionController.instance.saveFactions();
                    for (int i = 0; i < edits.size(); i++) {
                        Faction faction = FactionController.instance.getFaction(edits.getCompound(i).getInt("Slot"));
                        noppes.npcs.packets.Packets.sendAll(new noppes.npcs.packets.client.PacketSyncUpdate(
                            faction.id, 1, faction.writeNBT(new CompoundTag())));
                    }
                }
                reply(player, canEdit(player), error, error.isEmpty());
            }
            context.get().setPacketHandled(true);
        }
    }

    public record Snapshot(CompoundTag data) {
        public static void encode(Snapshot msg, FriendlyByteBuf buf) { buf.writeNbt(msg.data); }
        public static Snapshot decode(FriendlyByteBuf buf) { return new Snapshot(buf.readNbt()); }
        public static void handle(Snapshot msg, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> top.cnpclines.faction.client.FactionClient.receive(msg.data));
            context.get().setPacketHandled(true);
        }
    }

    public static void register() {
        NetworkHandler.INSTANCE.messageBuilder(Request.class, 1, NetworkDirection.PLAY_TO_SERVER)
            .encoder(Request::encode).decoder(Request::decode).consumerMainThread(Request::handle).add();
        NetworkHandler.INSTANCE.messageBuilder(Save.class, 2, NetworkDirection.PLAY_TO_SERVER)
            .encoder(Save::encode).decoder(Save::decode).consumerMainThread(Save::handle).add();
        NetworkHandler.INSTANCE.messageBuilder(Snapshot.class, 3, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(Snapshot::encode).decoder(Snapshot::decode).consumerMainThread(Snapshot::handle).add();
    }

    private static boolean canEdit(ServerPlayer player) {
        return player.getMainHandItem().getItem() == CustomItems.wand
            && CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.GLOBAL_FACTION);
    }

    private static String validate(CompoundTag data) {
        if (data == null) return "cnpclines.faction.invalid";
        ListTag edits = data.getList("Factions", 10);
        if (edits.isEmpty() || edits.size() > FactionController.instance.factions.size())
            return "cnpclines.faction.invalid";
        Set<Integer> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < edits.size(); i++) {
            CompoundTag edit = edits.getCompound(i);
            String name = edit.getString("Name");
            if (!ids.add(edit.getInt("Slot")) || FactionController.instance.getFaction(edit.getInt("Slot")) == null
                    || name.isBlank() || name.length() > 20 || name.contains("\n") || name.contains("\r")
                    || !names.add(name)) return "cnpclines.faction.invalid_name";
            CompoundTag visual = edit.getCompound(FactionVisual.KEY);
            for (String key : visual.getAllKeys()) {
                if (!Set.of("Title", "Description", "Friendly", "Neutral", "Hostile", "ListIcon", "Flag", "Order",
                        "BarColor", "TitleSpans", "DescriptionSpans", "FriendlySpans", "NeutralSpans",
                        "HostileSpans", "NameSpans", "ListIconSpans", "NameSize", "TitleSize", "DescriptionSize",
                        "FriendlySize", "NeutralSize", "HostileSize", "ListIconSize", "PointsSize").contains(key))
                    return "cnpclines.faction.invalid";
                if (key.endsWith("Size") && (!visual.contains(key, 3) || visual.getInt(key) < 6 || visual.getInt(key) > 24))
                    return "cnpclines.faction.invalid";
                if (!key.equals("Order") && !key.equals("BarColor") && !key.endsWith("Size")
                        && visual.getString(key).length() > (key.endsWith("Spans") ? 32768 : 8192))
                    return "cnpclines.faction.invalid";
            }
            String flag = visual.getString("Flag");
            if (flag.length() > 320 || (!flag.isEmpty() && ResourceLocation.tryParse(flag) == null))
                return "cnpclines.faction.invalid";
        }
        for (Faction existing : FactionController.instance.factions.values()) {
            if (!ids.contains(existing.id) && names.contains(existing.name)) return "cnpclines.faction.invalid_name";
        }
        return "";
    }

    private static void reply(ServerPlayer player, boolean edit, String error, boolean saved) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Edit", edit);
        tag.putBoolean("Saved", saved);
        tag.putString("Error", error);
        ListTag list = new ListTag();
        var data = PlayerData.get(player).factionData;
        for (Faction faction : FactionController.instance.factions.values()) {
            if (!edit && (faction.hideFaction || !data.factionData.containsKey(faction.id))) continue;
            CompoundTag entry = faction.writeNBT(new CompoundTag());
            entry.putInt("Points", data.factionData.getOrDefault(faction.id, faction.defaultPoints));
            list.add(entry);
        }
        tag.put("Factions", list);
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(tag));
    }
}
