package top.cnpclines.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import top.cnpclines.client.ClientMessageOverlay;

public class OpenCustomBarOverlayPacket implements IMessage {
    private String message;
    private String name;
    private String color;
    private String icon;
    private int maxSymbolsFirstLine;
    private int maxSymbolsOtherLines;

    public OpenCustomBarOverlayPacket() {
    }

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

    @Override
    public void fromBytes(ByteBuf buf) {
        this.message = ByteBufUtils.readUTF8String(buf);
        this.name = ByteBufUtils.readUTF8String(buf);
        this.color = ByteBufUtils.readUTF8String(buf);
        this.icon = ByteBufUtils.readUTF8String(buf);
        this.maxSymbolsFirstLine = buf.readInt();
        this.maxSymbolsOtherLines = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.message);
        ByteBufUtils.writeUTF8String(buf, this.name);
        ByteBufUtils.writeUTF8String(buf, this.color);
        ByteBufUtils.writeUTF8String(buf, this.icon);
        buf.writeInt(this.maxSymbolsFirstLine);
        buf.writeInt(this.maxSymbolsOtherLines);
    }

    public static class Handler implements IMessageHandler<OpenCustomBarOverlayPacket, IMessage> {
        @Override
        public IMessage onMessage(final OpenCustomBarOverlayPacket message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> ClientMessageOverlay.show(message));
            return null;
        }
    }
}
