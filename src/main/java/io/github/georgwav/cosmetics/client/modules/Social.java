package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;

/**
 * Social: streamer mode, for recording and streaming (your name reads "You", you wear the default skin, private
 * messages are hidden, and your coordinates and the server's address are hidden from the
 * HUD modules and from your Discord status while it's on), and your Discord status ("Playing on a server", how long,
 * Sol Client's theme), through the player's own Discord application id (Settings › Discord; D9).
 */
public final class Social extends Module {
    private final Setting.Toggle streamer = add(new Setting.Toggle("streamer_mode", true));
    private final Setting.Toggle discord = add(new Setting.Toggle("discord", true));
    private final Setting.Toggle discordServer = add(new Setting.Toggle("discord_server", true));
    private final Setting.Text discordApp = add(new Setting.Text("discord_app_id", "", 24));
    private long joinedAt;
    private String lastPlace;

    public Social() {
        super("social", Category.CHAT, "people");
    }

    @Override
    protected void onDisable(net.minecraft.client.Minecraft mc) {
        io.github.georgwav.cosmetics.sol.client.DiscordRpc.stop();
    }

    @Override
    protected void tick(net.minecraft.client.Minecraft mc) {
        if (!discord.get() || discordApp.get().isBlank()) {
            io.github.georgwav.cosmetics.sol.client.DiscordRpc.stop();
            return;
        }
        String place;
        if (mc.level == null) {
            place = net.minecraft.network.chat.Component.translatable("sol.discord.menus").getString();
        } else if (mc.isLocalServer()) {
            place = net.minecraft.network.chat.Component.translatable("sol.discord.singleplayer").getString();
        } else {
            var data = mc.getCurrentServer();
            String address = data == null || mc.isLocalServer() ? null : data.ip;
            place = address != null && discordServer.get() && !streaming()
                    ? net.minecraft.network.chat.Component.translatable("sol.discord.server", io.github.georgwav.cosmetics.sol.config.SolProfiles.host(address)).getString()
                    : net.minecraft.network.chat.Component.translatable("sol.discord.multiplayer").getString();
        }
        if (!place.equals(lastPlace)) {
            lastPlace = place;
            joinedAt = System.currentTimeMillis();
        }
        String theme = io.github.georgwav.cosmetics.sol.ui.kit.Widgets.themeName(io.github.georgwav.cosmetics.sol.theme.ThemeState.baseEntry());
        io.github.georgwav.cosmetics.sol.client.DiscordRpc.show(discordApp.get(), new io.github.georgwav.cosmetics.sol.client.DiscordRpc.Activity(
                place, net.minecraft.network.chat.Component.translatable("sol.discord.state", theme).getString(), joinedAt, "sol", "Sol Client"));
    }

    /** Whether streamer mode hides your coordinates and the server now. */
    public static boolean streaming() {
        Social s = Modules.SOCIAL;
        return s.enabled() && s.streamer.get();
    }
}
