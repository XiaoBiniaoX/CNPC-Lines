package top.cnpclines.faction;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

@Mod.EventBusSubscriber(modid = "cnpclines")
public final class FactionProtection {
    @SubscribeEvent
    public static void damaged(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide
                || event.getAmount() <= 0 || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == player || !attacker.isAlive()) return;
        for (EntityNPCInterface npc : player.level().getEntitiesOfClass(EntityNPCInterface.class,
                player.getBoundingBox().inflate(32, 16, 32))) {
            if (npc.isKilled() || !npc.advanced.defendFaction || !npc.getFaction().isFriendlyToPlayer(player)
                    || attacker == npc || attacker == npc.getOwner()
                    || (!npc.canNpcSee(player) && !npc.ais.directLOS && !npc.canNpcSee(attacker))) continue;
            npc.onAttack(attacker);
        }
    }
}
