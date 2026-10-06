package top.cnpclines.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.client.PacketChatBubble;
import noppes.npcs.shared.client.util.NoppesStringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpclines.client.NpcLineQueue;

@Mixin(value = PacketChatBubble.class, remap = false)
public class PacketChatBubbleMixin {
    @Shadow
    @Final
    private int id;

    @Shadow
    @Final
    private Component message;

    @Shadow
    @Final
    private boolean showMessage;

    @Inject(method = "handle()V", at = @At("HEAD"), cancellable = true)
    private void cnpclines$rerouteToOverlay(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity entity = mc.level.getEntity(this.id);
            if (entity instanceof EntityNPCInterface npc) {
                Player player = mc.player;
                String text = NoppesStringUtils.formatText(this.message, player, npc);
                if (this.showMessage) {
                    NpcLineQueue.enqueue(text, npc);
                }
            }
        }
        ci.cancel();
    }
}
