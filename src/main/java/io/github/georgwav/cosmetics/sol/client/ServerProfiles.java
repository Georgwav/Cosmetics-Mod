package io.github.georgwav.cosmetics.sol.client;

import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Server profiles: joining a server a profile is kept for switches to that profile, and leaving goes back to the
 * one you had. The profile popover keeps a profile for the server you're on.
 */
public final class ServerProfiles {
    private static String before;

    private ServerProfiles() {
    }

    public static void register() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> joined(mc));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(ServerProfiles::left));
    }

    /** The server you're on (its address), or null in single player. */
    public static String current() {
        var data = Minecraft.getInstance().getCurrentServer();
        return data == null || Minecraft.getInstance().isLocalServer() ? null : data.ip;
    }

    private static void joined(Minecraft mc) {
        String address = current();
        String profile = address == null ? null : SolProfiles.forServer(address);
        if (profile == null || profile.equals(SolProfiles.active())) {
            return;
        }
        String was = SolProfiles.active();
        if (SolProfiles.switchTo(profile)) {
            before = was;
            Notifications.post(Component.translatable("sol.notify.server_profile", profile, SolProfiles.host(address)).getString());
        }
    }

    private static void left() {
        if (before != null) {
            String back = before;
            before = null;
            SolProfiles.switchTo(back);
        }
    }
}
