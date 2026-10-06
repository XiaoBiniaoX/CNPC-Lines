package top.cnpclines.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

public final class NetworkHandler {
    public static final SimpleNetworkWrapper INSTANCE =
        NetworkRegistry.INSTANCE.newSimpleChannel("cnpclines:main");

    private NetworkHandler() {
    }

    public static void registerMessages() {
        INSTANCE.registerMessage(
            OpenCustomBarOverlayPacket.Handler.class,
            OpenCustomBarOverlayPacket.class,
            0,
            net.minecraftforge.fml.relauncher.Side.CLIENT
        );
    }

    public static void sendToPlayer(EntityPlayerMP player, OpenCustomBarOverlayPacket packet) {
        INSTANCE.sendTo(packet, player);
    }
}
