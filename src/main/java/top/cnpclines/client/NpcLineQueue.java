package top.cnpclines.client;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpclines.CNPCLines;
import top.cnpclines.config.ClientConfig;
import top.cnpclines.network.OpenCustomBarOverlayPacket;

@Mod.EventBusSubscriber(modid = CNPCLines.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcLineQueue {
    private static final long DISPLAY_LAST_MS = 5000L;
    private static final long DISPLAY_SHORT_MS = 1000L;
    private static final long FADE_MS = 700L;
    private static final long ICON_WAIT_MS = 600L;

    private static final Deque<Entry> queue = new ArrayDeque<>();
    private static boolean showing = false;
    private static boolean fading = false;
    private static long showStart = 0L;
    private static long fadeStart = 0L;

    private NpcLineQueue() {
    }

    public static synchronized void enqueue(String text, EntityNPCInterface npc) {
        if (!ClientConfig.replaceNpcLines) {
            return;
        }
        String name = npc.m_7755_().getString();
        queue.addLast(new Entry(text, name, npc, System.currentTimeMillis()));
    }

    @SubscribeEvent
    public static synchronized void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        NpcSkinIcon.drain();
        if (!ClientConfig.replaceNpcLines) {
            queue.clear();
            showing = false;
            fading = false;
            ClientMessageOverlay.clearNpc();
            return;
        }
        long now = System.currentTimeMillis();
        if (!showing) {
            if (!queue.isEmpty()) {
                showNext(now);
            }
            return;
        }
        if (!fading) {
            long displayLimit = queue.isEmpty() ? DISPLAY_LAST_MS : DISPLAY_SHORT_MS;
            if (now - showStart >= displayLimit) {
                ClientMessageOverlay.fadeOutNpc((int) FADE_MS);
                fading = true;
                fadeStart = now;
            }
        } else if (now - fadeStart >= FADE_MS) {
            showing = false;
            fading = false;
            if (!queue.isEmpty()) {
                showNext(now);
            }
        }
    }

    private static void showNext(long now) {
        Entry entry = queue.peek();
        if (entry == null) {
            return;
        }
        String icon = NpcSkinIcon.peek(entry.npc);
        if (icon.isEmpty() && NpcSkinIcon.isInflight(entry.npc)
            && now - entry.enqueueAt < ICON_WAIT_MS) {
            return;
        }
        queue.poll();
        ClientMessageOverlay.showNpc(
            new OpenCustomBarOverlayPacket(entry.text, entry.name, "#FFFFFF", icon, 32, 40)
        );
        showing = true;
        fading = false;
        showStart = now;
    }

    private static final class Entry {
        final String text;
        final String name;
        final EntityNPCInterface npc;
        final long enqueueAt;

        Entry(String text, String name, EntityNPCInterface npc, long enqueueAt) {
            this.text = text;
            this.name = name;
            this.npc = npc;
            this.enqueueAt = enqueueAt;
        }
    }
}
