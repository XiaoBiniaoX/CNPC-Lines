package top.cnpclines.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpclines.client.ClientMessageOverlay;

public class OpenCustomBarOverlayPacket implements CustomPacketPayload {
    public static final Type<OpenCustomBarOverlayPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("cnpclines", "main"));
    public static final StreamCodec<FriendlyByteBuf, OpenCustomBarOverlayPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> packet.encode(buf),
        OpenCustomBarOverlayPacket::decode
    );

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenCustomBarOverlayPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientMessageOverlay.show(packet);
            }
        });
    }
}
