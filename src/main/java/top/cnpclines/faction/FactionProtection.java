package top.cnpclines.faction;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import noppes.npcs.entity.EntityNPCInterface;

@Mod.EventBusSubscriber(modid = "cnpclines")
public final class FactionProtection {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damaged(LivingDamageEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer) || event.getEntityLiving().world.isRemote || event.getAmount() <= 0
                || !(event.getSource().getTrueSource() instanceof EntityLivingBase)) return;
        EntityPlayer player = (EntityPlayer)event.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase)event.getSource().getTrueSource();
        if (attacker == player || !attacker.isEntityAlive()) return;
        for (EntityNPCInterface npc : player.world.getEntitiesWithinAABB(EntityNPCInterface.class, player.getEntityBoundingBox().grow(32, 16, 32))) {
            if (npc.isKilled() || !npc.advanced.defendFaction || !npc.getFaction().isFriendlyToPlayer(player)
                || attacker == npc || attacker == npc.getOwner() || (!npc.canSee(player) && !npc.ais.directLOS && !npc.canSee(attacker))) continue;
            npc.onAttack(attacker);
        }
    }
}
