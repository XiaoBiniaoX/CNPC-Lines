package top.cnpclines.network;

import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import top.cnpclines.faction.FactionPackets;

public final class NetworkHandler {
    // Installed by the physical client entry point; common classes never reference GUI types.
    public static Consumer<OpenCustomBarOverlayPacket> overlay = packet -> {};
    public static Consumer<CompoundTag> factionSnapshot = data -> {};
    private NetworkHandler() {
    }

    public static void registerMessages(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.2");
        registrar.playToClient(
            OpenCustomBarOverlayPacket.TYPE,
            OpenCustomBarOverlayPacket.STREAM_CODEC,
            OpenCustomBarOverlayPacket::handle
        );
        FactionPackets.register(registrar);
    }

    public static void sendToPlayer(ServerPlayer player, OpenCustomBarOverlayPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
