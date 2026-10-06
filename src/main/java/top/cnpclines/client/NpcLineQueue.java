package top.cnpclines.client;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import noppes.npcs.entity.EntityNPCInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.cnpclines.CNPCLines;
import top.cnpclines.network.OpenCustomBarOverlayPacket;

public final class NpcLineQueue {
    private static final Logger LOGGER = LogManager.getLogger(CNPCLines.MOD_ID);
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
        String name = npc.getName();
        queue.addLast(new Entry(text, name, npc, System.currentTimeMillis()));
        LOGGER.info("[cnpclines] NPC line queued: {}", text);
    }

    @SubscribeEvent
    public static synchronized void onClientTick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) {
            return;
        }
        NpcSkinIcon.drain();
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
                HudOverlayManager.fadeOutAll((int) FADE_MS);
                fading = true;
                fadeStart = now;
                LOGGER.info("[cnpclines] line fade-start queue={}", queue.size());
            }
        } else if (now - fadeStart >= FADE_MS) {
            showing = false;
            fading = false;
            LOGGER.info("[cnpclines] line fade-end queue={}", queue.size());
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
        LOGGER.info("[cnpclines] line show: {}", entry.text);
        ClientMessageOverlay.show(
            new OpenCustomBarOverlayPacket(entry.text, entry.name, "#FFFFFF", icon, 32, 40),
            0L
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
