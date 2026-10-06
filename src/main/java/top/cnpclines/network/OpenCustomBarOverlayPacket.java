package top.cnpclines.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;
import top.cnpclines.client.ClientMessageOverlay;

public class OpenCustomBarOverlayPacket {
    private final String message;
    private final String name;
    private final String color;
    private final String icon;
    private final int maxSymbolsFirstLine;
    private final int maxSymbolsOtherLines;

    public OpenCustomBarOverlayPacket(
        String message, String name, String color, String icon, int maxSymbolsFirstLine, int maxSymbolsOtherLines
    ) {
        this.message = message;
        this.name = name;
        this.color = color;
        this.icon = icon;
        this.maxSymbolsFirstLine = maxSymbolsFirstLine;
        this.maxSymbolsOtherLines = maxSymbolsOtherLines;
    }

    public String getMessage() {
        return this.message;
    }

    public String getName() {
        return this.name;
    }

    public String getColor() {
        return this.color;
    }

    public String getIcon() {
        return this.icon;
    }

    public int getMaxSymbolsFirstLine() {
        return this.maxSymbolsFirstLine;
    }

    public int getMaxSymbolsOtherLines() {
        return this.maxSymbolsOtherLines;
    }

    public static OpenCustomBarOverlayPacket decode(FriendlyByteBuf buf) {
        return new OpenCustomBarOverlayPacket(
            buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
            buf.readInt(), buf.readInt()
        );
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.message);
        buf.writeUtf(this.name);
        buf.writeUtf(this.color);
        buf.writeUtf(this.icon);
        buf.writeInt(this.maxSymbolsFirstLine);
        buf.writeInt(this.maxSymbolsOtherLines);
    }

    public static void handle(OpenCustomBarOverlayPacket packet, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientMessageOverlay.show(packet);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
