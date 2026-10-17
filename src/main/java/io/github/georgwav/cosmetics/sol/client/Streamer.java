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

    /** Your name as the game knows it (the account's name without a player), or null. */
    private static String ownName() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            return player.getScoreboardName();
        }
        var user = Minecraft.getInstance().getUser();
        return user == null ? null : user.getName();
    }

    /** Your account name to show in Sol's own screens: "You" while streaming. */
    public static String shownName(String name) {
        return on() ? Component.translatable("sol.streamer.you").getString() : name;
    }

    /** A chat line as a recording may show it: private messages hidden, your name as "You". */
    public static Component scrub(Component message) {
        if (!on() || message == null) {
            return message;
        }
        try {
            return scrubbed(message);
        } catch (RuntimeException e) {
            // A line that couldn't be looked through is hidden rather than shown as it is.
            io.github.georgwav.cosmetics.sol.util.LogOnce.warn("streamer", "Streamer mode couldn't read a chat line: {}", e.toString());
            return Component.translatable("sol.streamer.hidden").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
    }

    private static Component scrubbed(Component message) {
        if (message.getContents() instanceof TranslatableContents t
                && (t.getKey().equals("commands.message.display.incoming") || t.getKey().equals("commands.message.display.outgoing"))) {
            return Component.translatable("sol.streamer.private").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
        String name = ownName();
        if (name == null || name.isEmpty() || !message.getString().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
            return message;
        }
        String you = Component.translatable("sol.streamer.you").getString();
        if (replace(message.getString(), name, you).equals(message.getString())) {
            return message;
        }
        // The line names you: its hover cards, click actions and shift-click text would still give your name (a
        // sender's card has the name and UUID; clicking it suggests /tell with it), so they go with it.
        MutableComponent out = Component.empty();
        message.visit((style, text) -> {
            out.append(Component.literal(replace(text, name, you)).setStyle(style.withHoverEvent(null).withClickEvent(null).withInsertion(null)));
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

    /**
     * {@code text} with every {@code name} in it (any case) as {@code you}: whole names only, not inside a longer word
     * (a name is letters, digits and underscores, so "Ash" leaves "Crash" alone).
     */
    static String replace(String text, String name, String you) {
        StringBuilder b = null;
        int from = 0;
        String lower = text.toLowerCase(Locale.ROOT), n = name.toLowerCase(Locale.ROOT);
        for (int at = lower.indexOf(n); at >= 0; at = lower.indexOf(n, at + 1)) {
            int end = at + n.length();
            if (at < from || at > 0 && nameChar(text.charAt(at - 1)) || end < text.length() && nameChar(text.charAt(end))) {
                continue;
            }
            if (b == null) {
                b = new StringBuilder(text.length());
            }
            b.append(text, from, at).append(you);
            from = end;
        }
        return b == null ? text : b.append(text, from, text.length()).toString();
    }

    private static boolean nameChar(char c) {
        return c == '_' || c < 128 && Character.isLetterOrDigit(c);
    }
}
