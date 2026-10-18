package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.chat.Emoji;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Sol Chat: timestamps (click one to copy its message), repeated messages stacked into one with a count, a longer
 * history to scroll back through, and {@code :name:} shortcodes turned into symbols as you send. The chat's look
 * follows Sol UI (its "Chat" part).
 */
public final class SolChat extends Module {
    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm", Locale.ROOT);
    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private final Setting.Toggle timestamps = add(new Setting.Toggle("timestamps", true).group("timestamps"));
    private final Setting.Choice format = add(new Setting.Choice("format", "24h", List.of("24h", "12h", "seconds")).group("timestamps"));
    /** Clicking a message's time copies the message. */
    private final Setting.Toggle copy = add(new Setting.Toggle("copy", true).group("timestamps"));
    private final Setting.Toggle compact = add(new Setting.Toggle("compact", true).group("messages"));
    private final Setting.Toggle emoji = add(new Setting.Toggle("emoji", true).group("messages"));
    /** How many messages the chat keeps (the game keeps 100). */
    private final Setting.Slider history = add(new Setting.Slider("history", 1000, 100, 5000, 100, v -> Integer.toString((int) Math.round(v))).group("messages"));

    private String lastRaw;
    private int repeats;

    public SolChat() {
        super("sol_chat", Category.CHAT, "chat");
    }

    /** How many messages the chat keeps, given the game's number. */
    /** Whether the emoji shortcodes and picker are on. */
    public static boolean emojiOn() {
        SolChat m = Modules.SOL_CHAT;
        return m.enabled() && m.emoji.get();
    }

    public static int history(int game) {
        SolChat m = Modules.SOL_CHAT;
        return m.enabled() ? (int) Math.round(m.history.get()) : game;
    }

    /** What a message becomes as it reaches the chat. */
    public record Added(Component component, boolean replacesLast) {
    }

    /** A message on its way into the chat: stacked and stamped; null leaves it as it is. */
    public static Added process(Component message) {
        SolChat m = Modules.SOL_CHAT;
        if (!m.enabled()) {
            return null;
        }
        String raw = message.getString();
        boolean again = m.compact.get() && raw.equals(m.lastRaw);
        m.repeats = again ? m.repeats + 1 : 1;
        m.lastRaw = raw;
        MutableComponent out = Component.empty();
        if (m.timestamps.get()) {
            String time = LocalTime.now().format(switch (m.format.get()) {
                case "12h" -> H12;
                case "seconds" -> SECONDS;
                default -> H24;
            });
            MutableComponent stamp = Component.literal(time + " ").withStyle(ChatFormatting.DARK_GRAY);
            if (m.copy.get()) {
                stamp = stamp.withStyle(style -> style.withClickEvent(copyEvent(raw))
                        .withHoverEvent(showText(Component.translatable("sol.chat.copy"))));
            }
            out.append(stamp);
        }
        out.append(message);
        if (again) {
            out.append(Component.literal(" \u00d7" + m.repeats).withStyle(ChatFormatting.GRAY));
        }
        return new Added(out, again);
    }

    private static net.minecraft.network.chat.ClickEvent copyEvent(String text) {
        //? if >=1.21.5 {
        return new net.minecraft.network.chat.ClickEvent.CopyToClipboard(text);
        //?} else {
        /*return new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, text);
        *///?}
    }

    private static net.minecraft.network.chat.HoverEvent showText(Component text) {
        //? if >=1.21.5 {
        return new net.minecraft.network.chat.HoverEvent.ShowText(text);
        //?} else {
        /*return new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, text);
        *///?}
    }

    /** A line you send, with its shortcodes turned into symbols. */
    public static String outgoing(String text) {
        SolChat m = Modules.SOL_CHAT;
        return m.enabled() && m.emoji.get() ? fit(Emoji.replace(text)) : text;
    }

    /** The game sends at most 256 characters: a shortcode can make a full line longer, so it's cut back. */
    static String fit(String text) {
        if (text.length() <= 256) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(255)) ? 255 : 256;
        return text.substring(0, end);
    }

    @Override
    protected void onDisable(Minecraft mc) {
        lastRaw = null;
        repeats = 0;
    }
}
