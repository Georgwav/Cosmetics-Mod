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
 * Sol Chat: timestamps (click one to copy its message), the sender's face (from 1.21.9), repeated messages stacked into one with a count, mentions of your name (or your own words)
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
    /** Clicking a message's time copies the message. */
    private final Setting.Toggle copy = add(new Setting.Toggle("copy", true));
    private final Setting.Toggle compact = add(new Setting.Toggle("compact", true));
    private final Setting.Toggle mentions = add(new Setting.Toggle("mentions", true));
    private final Setting.Toggle mentionSound = add(new Setting.Toggle("mention_sound", true));
    private final Setting.Text mentionWords = add(new Setting.Text("mention_words", "", 80).advanced());
    private final Setting.Toggle emoji = add(new Setting.Toggle("emoji", true));
    //? if >=1.21.9 {
    /** The sender's face before each message (the game draws player heads in text from 1.21.9). */
    private final Setting.Toggle heads = add(new Setting.Toggle("heads", true));
    //?}
    private final Setting.Text[] autoText = {
            add(new Setting.Text("auto_text_1", "gg", 100).advanced()),
            add(new Setting.Text("auto_text_2", "", 100).advanced()),
            add(new Setting.Text("auto_text_3", "", 100).advanced())};

    private final Mentions matcher = new Mentions();
    private static final io.github.georgwav.cosmetics.sol.chat.ChatFilter FILTER = new io.github.georgwav.cosmetics.sol.chat.ChatFilter();
    /** The messages marked as mentions (for the Mentions tab); forgotten with the chat's own lines. */
    private static final java.util.Set<Component> MENTIONED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private String lastRaw;
    private int repeats;

    public SolChat() {
        super("sol_chat", Category.CHAT, "chat");
        for (int i = 1; i <= autoText.length; i++) {
            addKey("auto_text_" + i);
        }
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof net.minecraft.client.gui.screens.ChatScreen && enabled()) {
                chatControls(mc, screen, w, h);
            }
        });
    }

    /** The chat's tabs (All, Players, Mentions) and search, at the right end above the line you type in. */
    private static void chatControls(Minecraft mc, net.minecraft.client.gui.screens.Screen screen, int w, int h) {
        //? if >=26.1 {
        /*var widgets = net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen);
        *///?} else {
        var widgets = net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen);
        //?}
        int y = h - 30, right = w - 4, boxW = 110;
        var search = new net.minecraft.client.gui.components.EditBox(mc.font, right - boxW, y, boxW, 14,
                Component.translatable("sol.chat.search"));
        search.setHint(Component.translatable("sol.chat.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(FILTER.query());
        search.setResponder(q -> {
            if (FILTER.query(q)) {
                refresh(mc);
            }
        });
        widgets.add(search);
        var tabs = io.github.georgwav.cosmetics.sol.chat.ChatFilter.Tab.values();
        java.util.List<net.minecraft.client.gui.components.Button> buttons = new java.util.ArrayList<>();
        int x = right - boxW - 4;
        for (int i = tabs.length - 1; i >= 0; i--) {
            var tab = tabs[i];
            int bw = mc.font.width(tabLabel(tab, true)) + 10;
            x -= bw;
            var button = net.minecraft.client.gui.components.Button.builder(tabLabel(tab, FILTER.tab() == tab), b -> {
                if (FILTER.tab(tab)) {
                    refresh(mc);
                }
                for (int k = 0; k < buttons.size(); k++) {
                    buttons.get(k).setMessage(tabLabel(tabs[tabs.length - 1 - k], FILTER.tab() == tabs[tabs.length - 1 - k]));
                }
            }).bounds(x, y, bw, 14).build();
            buttons.add(button);
            widgets.add(button);
            x -= 2;
        }
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.remove(screen).register(s -> {
            if (FILTER.reset()) {
                refresh(mc);
            }
        });
    }

    private static Component tabLabel(io.github.georgwav.cosmetics.sol.chat.ChatFilter.Tab tab, boolean selected) {
        Component name = Component.translatable("sol.chat.tab." + tab.name().toLowerCase(Locale.ROOT));
        return selected ? Component.literal("\u2022 ").append(name).withStyle(s -> s.withColor(mentionColor())) : name;
    }

    private static void refresh(Minecraft mc) {
        //? if >=26.2 {
        /*var component = mc.gui.hud.getChat();
        *///?} else {
        var component = mc.gui.getChat();
        //?}
        if (component instanceof io.github.georgwav.cosmetics.sol.chat.ChatRefresh chat) {
            chat.cosmeticsmod$refresh();
        }
    }

    /** Whether the chat's tabs or search leave a message out now. */
    public static boolean hides(Component content, boolean signed) {
        if (!FILTER.active() || content == null) {
            return false;
        }
        java.util.List<String> online = new java.util.ArrayList<>();
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            for (var info : connection.getOnlinePlayers()) {
                //? if >=1.21.9 {
                online.add(info.getProfile().name());
                //?} else {
                /*online.add(info.getProfile().getName());
                *///?}
            }
        }
        boolean hidden = !FILTER.shows(content.getString(), signed, MENTIONED.contains(content), online);
        if (hidden) {
            filteredOut++;
        }
        return hidden;
    }

    /** For the in-game test: messages the tabs or search left out. */
    public static int filteredOut;

    /** For the in-game test: searches the chat (or stops with an empty text) and rebuilds what it shows. */
    public static void search(String text) {
        FILTER.reset();
        FILTER.query(text);
        refresh(Minecraft.getInstance());
    }

    /** For the in-game test: the filter (the chat's tabs and search). */
    public static io.github.georgwav.cosmetics.sol.chat.ChatFilter filter() {
        return FILTER;
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
            MutableComponent stamp = Component.literal(time + " ").withStyle(ChatFormatting.DARK_GRAY);
            if (m.copy.get()) {
                stamp = stamp.withStyle(style -> style.withClickEvent(copyEvent(raw))
                        .withHoverEvent(showText(Component.translatable("sol.chat.copy"))));
            }
            out.append(stamp);
        }
        //? if >=1.21.9 {
        if (m.heads.get()) {
            Component head = head(Minecraft.getInstance(), raw);
            if (head != null) {
                out.append(head).append(Component.literal(" "));
            }
        }
        //?}
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
        if (mention) {
            MENTIONED.add(out);
        }
        return new Added(out, again, mention);
    }

    //? if >=1.21.9 {
    /** For the in-game test: heads put before messages. */
    public static int headsShown;

    /** The face of whoever in the tab list sent the line, as a player head in the text; null when none did. */
    private static Component head(Minecraft mc, String raw) {
        var connection = mc.getConnection();
        if (connection == null) {
            return null;
        }
        java.util.Map<String, net.minecraft.client.multiplayer.PlayerInfo> byName = new java.util.HashMap<>();
        for (var info : connection.getOnlinePlayers()) {
            byName.put(info.getProfile().name(), info);
        }
        String sender = io.github.georgwav.cosmetics.sol.chat.ChatSender.of(raw, byName.keySet());
        if (sender == null) {
            return null;
        }
        headsShown++;
        return Component.object(new net.minecraft.network.chat.contents.objects.PlayerSprite(
                net.minecraft.world.item.component.ResolvableProfile.createResolved(byName.get(sender).getProfile()), true));
    }
    //?}

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

    /** The colour of a mention's marker: the theme's accent. */
    public static int mentionColor() {
        return ThemeState.tokens().accent & 0xFFFFFF;
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
