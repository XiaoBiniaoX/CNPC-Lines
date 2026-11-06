package top.cnpclines.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import top.cnpclines.CNPCLines;
import top.cnpclines.faction.client.FactionClient;
import top.cnpclines.network.NetworkHandler;

/** NeoForge creates this entry point only on the physical client. */
@Mod(value = CNPCLines.MOD_ID, dist = Dist.CLIENT)
public final class CNPCLinesClient {
    public CNPCLinesClient(ModContainer container) {
        MessageOverlayRenderer.registerConfigScreen(container);
        NetworkHandler.overlay = ClientMessageOverlay::show;
        NetworkHandler.factionSnapshot = FactionClient::receive;
    }
}
