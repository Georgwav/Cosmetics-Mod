package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * Hypixel: Auto GG (your line, said once when a game ends: Hypixel's end-of-game summary lines are what it listens
 * for), only while you're on Hypixel.
 */
public final class Hypixel extends Module {
    private final Setting.Toggle autoGg = add(new Setting.Toggle("auto_gg", true));
    private final Setting.Text ggText = add(new Setting.Text("gg_text", "gg", 60));
    private static final List<String> ENDS = List.of("1st killer", "winner:", "winners:", "winner -", "reward summary",
            "your overall winstreak", "1st place");
    private long lastGg;
    private int pending = -1;

    public Hypixel() {
        super("hypixel", Category.CHAT, "star");
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && enabled() && autoGg.get() && onHypixel()) {
                String line = message.getString().toLowerCase(Locale.ROOT).trim();
                for (String end : ENDS) {
                    if (line.contains(end) && System.currentTimeMillis() - lastGg > 15_000L) {
                        lastGg = System.currentTimeMillis();
                        pending = 20;
                        break;
                    }
                }
            }
        });
    }

    static boolean onHypixel() {
        var server = Minecraft.getInstance().getCurrentServer();
        return server != null && server.ip.toLowerCase(Locale.ROOT).contains("hypixel.net");
    }

    @Override
    protected void tick(Minecraft mc) {
        if (pending > 0 && --pending == 0 && mc.player != null) {
            String text = SolChat.outgoing(ggText.get().trim());
            if (!text.isEmpty()) {
                mc.player.connection.sendChat(text);
            }
        }
    }

    /** For the self-test: whether a line counts as a game's end. */
    public static boolean endsGame(String line) {
        String l = line.toLowerCase(Locale.ROOT);
        return ENDS.stream().anyMatch(l::contains);
    }
}
