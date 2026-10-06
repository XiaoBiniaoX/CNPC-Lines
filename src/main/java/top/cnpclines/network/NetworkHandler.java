package top.cnpclines.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("cnpclines", "main"), () -> "1.1", "1.1"::equals, "1.1"::equals
    );

    private NetworkHandler() {
    }

    public static void registerMessages() {
        INSTANCE.messageBuilder(OpenCustomBarOverlayPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(OpenCustomBarOverlayPacket::encode)
            .decoder(OpenCustomBarOverlayPacket::decode)
            .consumerMainThread(OpenCustomBarOverlayPacket::handle)
            .add();
    }

    public static void sendToPlayer(ServerPlayer player, OpenCustomBarOverlayPacket packet) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
