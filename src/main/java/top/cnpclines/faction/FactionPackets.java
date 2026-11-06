package top.cnpclines.faction;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import noppes.npcs.CustomItems;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.controllers.FactionController;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.controllers.data.PlayerData;
import top.cnpclines.network.NetworkHandler;

public final class FactionPackets {
    public record Request(boolean edit) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpclines", "faction_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeBoolean(msg.edit), buf -> new Request(buf.readBoolean()));
        @Override public Type<Request> type() { return TYPE; }
        public static void handle(Request msg, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) reply(player, msg.edit && canEdit(player), "", false);
            });
        }
    }

    public record Save(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Save> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpclines", "faction_save"));
        public static final StreamCodec<FriendlyByteBuf, Save> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeNbt(msg.data), buf -> new Save(buf.readNbt()));
        @Override public Type<Save> type() { return TYPE; }
        public static void handle(Save msg, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
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
            });
        }
    }

    public record Snapshot(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpclines", "faction_snapshot"));
        public static final StreamCodec<FriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeNbt(msg.data), buf -> new Snapshot(buf.readNbt()));
        @Override public Type<Snapshot> type() { return TYPE; }
        public static void handle(Snapshot msg, IPayloadContext context) {
            context.enqueueWork(() -> NetworkHandler.factionSnapshot.accept(msg.data));
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Request.TYPE, Request.STREAM_CODEC, Request::handle);
        registrar.playToServer(Save.TYPE, Save.STREAM_CODEC, Save::handle);
        registrar.playToClient(Snapshot.TYPE, Snapshot.STREAM_CODEC, Snapshot::handle);
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
        PacketDistributor.sendToPlayer(player, new Snapshot(tag));
    }
}
