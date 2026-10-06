package top.cnpclines.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkHandler {
    private NetworkHandler() {
    }

    public static void registerMessages(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.1");
        registrar.playToClient(
            OpenCustomBarOverlayPacket.TYPE,
            OpenCustomBarOverlayPacket.STREAM_CODEC,
            OpenCustomBarOverlayPacket::handle
        );
    }

    public static void sendToPlayer(ServerPlayer player, OpenCustomBarOverlayPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
