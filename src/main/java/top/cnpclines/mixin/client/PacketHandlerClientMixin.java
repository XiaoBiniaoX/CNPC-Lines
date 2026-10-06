package top.cnpclines.mixin.client;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import noppes.npcs.NoppesStringUtils;
import noppes.npcs.Server;
import noppes.npcs.client.PacketHandlerClient;
import noppes.npcs.constants.EnumPacketClient;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpclines.client.NpcLineQueue;

@Mixin(value = PacketHandlerClient.class, remap = false)
public class PacketHandlerClientMixin {

    @Inject(
        method = "client(Lio/netty/buffer/ByteBuf;Lnet/minecraft/entity/player/EntityPlayer;Lnoppes/npcs/constants/EnumPacketClient;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cnpclines$rerouteChatBubble(ByteBuf buffer, EntityPlayer player, EnumPacketClient type, CallbackInfo ci) {
        if (type != EnumPacketClient.CHATBUBBLE) {
            return;
        }
        try {
            int entityId = buffer.readInt();
            Entity entity = Minecraft.getMinecraft().world.getEntityByID(entityId);
            if (entity instanceof EntityNPCInterface) {
                EntityNPCInterface npc = (EntityNPCInterface) entity;
                String text = NoppesStringUtils.formatText(Server.readString(buffer), player, npc);
                boolean showMessage = buffer.readBoolean();
                if (showMessage) {
                    NpcLineQueue.enqueue(text, npc);
                }
            }
        } catch (Exception ignored) {
        }
        ci.cancel();
    }
}
