package io.github.georgwav.cosmetics.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.List;

/** Your latency to the server, as the player list has it. */
public final class PingHud extends TextHud {
    public PingHud() {
        super("ping", "signal", 0f, 0f, 4f, 22f);
    }

    @Override
    protected List<String> lines(Minecraft mc) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.player == null) {
            return List.of();
        }
        PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
        return info == null ? List.of() : List.of(info.getLatency() + " ms");
    }

    @Override
    protected List<String> sample(Minecraft mc) {
        return List.of("24 ms");
    }
}
