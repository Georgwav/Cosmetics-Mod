package io.github.georgwav.cosmetics.sol.client;

import io.github.georgwav.cosmetics.client.modules.Social;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Locale;
import java.util.Optional;

/**
 * Streamer mode (Social): what a recording shouldn't show. Your name reads "You" in chat, on your own name tag and in
 * the tab list; private messages, to you or from you, show as hidden; you wear the game's default skin; the server's
 * address is left out of the HUD, the Sol menu and Discord (each asks {@link Social#streaming()}).
 */
public final class Streamer {
    private Streamer() {
    }

    public static boolean on() {
        return Social.streaming();
    }

    /** Your name as the game knows it, or null without a player. */
    private static String ownName() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : player.getGameProfile() == null ? null : player.getScoreboardName();
    }

    /** A chat line as a recording may show it: private messages hidden, your name as "You". */
    public static Component scrub(Component message) {
        if (!on() || message == null) {
            return message;
        }
        if (message.getContents() instanceof TranslatableContents t
                && (t.getKey().equals("commands.message.display.incoming") || t.getKey().equals("commands.message.display.outgoing"))) {
            return Component.translatable("sol.streamer.private").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
        String name = ownName();
        if (name == null || name.isEmpty() || !message.getString().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
            return message;
        }
        String you = Component.translatable("sol.streamer.you").getString();
        MutableComponent out = Component.empty();
        message.visit((style, text) -> {
            out.append(Component.literal(replace(text, name, you)).setStyle(style));
            return Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    /** Whether {@code player} is you and streamer mode hides you. */
    public static boolean hides(java.util.UUID player) {
        var self = Minecraft.getInstance().player;
        return on() && self != null && player != null && player.equals(self.getUUID());
    }

    /** A name to show for {@code player}'s tag or row: "You" for yours while streaming, else as it is. */
    public static Component name(java.util.UUID player, Component shown) {
        if (shown == null || !hides(player)) {
            return shown;
        }
        return Component.translatable("sol.streamer.you").setStyle(shown.getStyle());
    }

    /** {@code text} with every {@code name} in it (any case) as {@code you}. */
    static String replace(String text, String name, String you) {
        StringBuilder b = null;
        int from = 0;
        String lower = text.toLowerCase(Locale.ROOT), n = name.toLowerCase(Locale.ROOT);
        for (int at = lower.indexOf(n); at >= 0; at = lower.indexOf(n, from)) {
            if (b == null) {
                b = new StringBuilder(text.length());
            }
            b.append(text, from, at).append(you);
            from = at + n.length();
        }
        return b == null ? text : b.append(text, from, text.length()).toString();
    }
}
