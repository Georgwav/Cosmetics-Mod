package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.chat.Emoji;
import io.github.georgwav.cosmetics.sol.chat.Mentions;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Sol Chat: timestamps, repeated messages stacked into one with a count, mentions of your name (or your own words)
 * marked in the accent with a soft sound, {@code :name:} shortcodes turned into symbols as you send, and three
 * auto-text keys that send a line (or a command) of yours. The chat's look follows Sol UI (its "Chat" part).
 * The game's chat history is kept between sessions by the game itself.
 */
public final class SolChat extends Module {
    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm", Locale.ROOT);
    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private final Setting.Toggle timestamps = add(new Setting.Toggle("timestamps", true));
    private final Setting.Choice format = add(new Setting.Choice("format", "24h", List.of("24h", "12h", "seconds")));
    private final Setting.Toggle compact = add(new Setting.Toggle("compact", true));
    private final Setting.Toggle mentions = add(new Setting.Toggle("mentions", true));
    private final Setting.Toggle mentionSound = add(new Setting.Toggle("mention_sound", true));
    private final Setting.Text mentionWords = add(new Setting.Text("mention_words", "", 80).advanced());
    private final Setting.Toggle emoji = add(new Setting.Toggle("emoji", true));
    private final Setting.Text[] autoText = {
            add(new Setting.Text("auto_text_1", "gg", 100).advanced()),
            add(new Setting.Text("auto_text_2", "", 100).advanced()),
            add(new Setting.Text("auto_text_3", "", 100).advanced())};

    private final Mentions matcher = new Mentions();
    private String lastRaw;
    private int repeats;

    public SolChat() {
        super("sol_chat", Category.CHAT, "chat");
        for (int i = 1; i <= autoText.length; i++) {
            addKey("auto_text_" + i);
        }
    }

    /** What a message becomes as it reaches the chat. */
    public record Added(Component component, boolean replacesLast, boolean mention) {
    }

    /** A message on its way into the chat: stacked, stamped and checked for mentions; null leaves it as it is. */
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
            out.append(Component.literal(time + " ").withStyle(ChatFormatting.DARK_GRAY));
        }
        out.append(message);
        if (again) {
            out.append(Component.literal(" ×" + m.repeats).withStyle(ChatFormatting.GRAY));
        }
        boolean mention = false;
        Minecraft mc = Minecraft.getInstance();
        if (m.mentions.get() && mc.player != null) {
            m.matcher.set(mc.player.getScoreboardName(), m.mentionWords.get());
            mention = m.matcher.mentions(raw);
            if (mention && m.mentionSound.get()) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.7f, 0.35f));
            }
        }
        return new Added(out, again, mention);
    }

    /** The colour of a mention's marker: the theme's accent. */
    public static int mentionColor() {
        return ThemeState.tokens().accent & 0xFFFFFF;
    }

    /** A line you send, with its shortcodes turned into symbols. */
    public static String outgoing(String text) {
        SolChat m = Modules.SOL_CHAT;
        return m.enabled() && m.emoji.get() ? Emoji.replace(text) : text;
    }

    @Override
    protected void tick(Minecraft mc) {
        for (int i = 0; i < autoText.length; i++) {
            var key = key("auto_text_" + (i + 1));
            while (key != null && key.consumeClick()) {
                String text = outgoing(autoText[i].get().trim());
                if (text.isEmpty() || mc.player == null) {
                    continue;
                }
                if (text.startsWith("/")) {
                    mc.player.connection.sendCommand(text.substring(1));
                } else {
                    mc.player.connection.sendChat(text);
                }
            }
        }
    }

    @Override
    protected void onDisable(Minecraft mc) {
        lastRaw = null;
        repeats = 0;
    }
}
