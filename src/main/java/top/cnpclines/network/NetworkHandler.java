package top.cnpclines.network;

import java.util.function.Consumer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import top.cnpclines.faction.FactionPackets;

public final class NetworkHandler {
    public static Consumer<OpenCustomBarOverlayPacket> overlay = packet -> {};
    public static Consumer<NBTTagCompound> factionSnapshot = data -> {};
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
        FactionPackets.register();
    }

    public static void sendToPlayer(EntityPlayerMP player, OpenCustomBarOverlayPacket packet) {
        INSTANCE.sendTo(packet, player);
    }
}
