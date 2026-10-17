package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Hypixel: Auto GG (your line, said once when a game ends: Hypixel's end-of-game summary lines are what it listens
 * for, and with Hypixel's Mod API only while you're in a game); Quickplay (its key opens every game and mode, a click
 * joins the queue); Play again (its key queues the mode you played last); Level head (players' network level
 * after their name, from Hypixel's API with your own key); and Bed Wars timers (the nearest diamond and emerald
 * generators' next spawn and the next event, at the top of the screen). Only while you're on Hypixel.
 */
public final class Hypixel extends Module {
    private final Setting.Toggle autoGg = add(new Setting.Toggle("auto_gg", true));
    private final Setting.Text ggText = add(new Setting.Text("gg_text", "gg", 60));
    /** Players' network level after their name, from Hypixel's API with your key (kept in the secrets file). */
    private final Setting.Toggle levelHead = add(new Setting.Toggle("level_head", false));
    private final Setting.Secret apiKey = add(new Setting.Secret("api_key", "hypixel_api_key", 64).advanced());
    /** In Bed Wars: the nearest generators' next spawn and the sidebar's next event, at the top of the screen. */
    private final Setting.Toggle bedwarsTimers = add(new Setting.Toggle("bedwars_timers", true));
    /** The timers' lines now (empty: none to show), and what they were made from. */
    private List<String> timerLines = List.of();
    private String timerKey = "";
    /** For the self-test: the made-up Bed Wars sidebar counts as Hypixel's. */
    public static boolean pretendForTest;
    private static final List<String> ENDS = List.of("1st killer", "winner:", "winners:", "winner -", "reward summary",
            "your overall winstreak", "1st place");
    private long lastGg;
    private int pending = -1;

    /** The mode played last (a {@code /play} name), from Hypixel's Mod API or Quickplay. */
    private String lastGame;

    public Hypixel() {
        super("hypixel", Category.CHAT, "star");
        addKey("quickplay");
        addKey("play_again");
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            var where = io.github.georgwav.cosmetics.sol.client.HypixelModApi.location();
            if (!overlay && enabled() && autoGg.get() && onHypixel() && (where == null || where.inGame())) {
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

    /** A player's name tag with their Hypixel network level after it, when Level head is on. */
    public static Component withLevel(net.minecraft.world.entity.Entity entity, Component name) {
        Hypixel h = Modules.HYPIXEL;
        if (name == null || !h.enabled() || !h.levelHead.get() || !(entity instanceof net.minecraft.world.entity.player.Player)
                || !onHypixelNow()) {
            return name;
        }
        int level = io.github.georgwav.cosmetics.sol.client.HypixelLevels.of(entity.getUUID(), h.apiKey.get());
        if (level < 0) {
            return name;
        }
        return name.copy().append(Component.literal(" Lv " + level).withStyle(net.minecraft.ChatFormatting.AQUA));
    }

    /** Whether you're on Hypixel now (the server's address, or Hypixel's Mod API said hello). */
    public static boolean onHypixelNow() {
        return onHypixel() || io.github.georgwav.cosmetics.sol.client.HypixelModApi.connected();
    }

    /** The mode played last, or null. */
    public static String lastGame() {
        return Modules.HYPIXEL.lastGame;
    }

    /** Joins a mode's queue with Hypixel's {@code /play}. */
    public static void play(Minecraft mc, String mode) {
        if (mc.player == null || !onHypixelNow()) {
            return;
        }
        Modules.HYPIXEL.lastGame = mode;
        mc.player.connection.sendCommand("play " + mode);
    }

    @Override
    protected void tick(Minecraft mc) {
        var where = io.github.georgwav.cosmetics.sol.client.HypixelModApi.location();
        if (where != null) {
            where.playId().ifPresent(id -> lastGame = id);
        }
        var quick = key("quickplay");
        while (quick != null && quick.consumeClick()) {
            if (io.github.georgwav.cosmetics.ui.ScreenOpener.current() == null) {
                io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.QuickplayScreen(null));
            }
        }
        var again = key("play_again");
        while (again != null && again.consumeClick()) {
            if (lastGame != null) {
                play(mc, lastGame);
            }
        }
        if (mc.level != null && mc.level.getGameTime() % 10 == 0) {
            updateTimers(mc);
        }
        if (pending > 0 && --pending == 0 && mc.player != null) {
            String text = SolChat.outgoing(ggText.get().trim());
            if (!text.isEmpty()) {
                mc.player.connection.sendChat(text);
            }
        }
    }

    /**
     * Twice a second in Bed Wars (Hypixel's Mod API says so, or the sidebar's title does): the nearest generators'
     * countdowns from the text floating over them, and the next event from the sidebar.
     */
    private void updateTimers(Minecraft mc) {
        var board = mc.level.getScoreboard();
        var objective = board.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
        var where = io.github.georgwav.cosmetics.sol.client.HypixelModApi.location();
        boolean bedWars = where != null && where.serverType().map("BEDWARS"::equalsIgnoreCase).orElse(false)
                || objective != null && io.github.georgwav.cosmetics.sol.client.BedWarsTimers.isBedWars(objective.getDisplayName().getString());
        if (!bedwarsTimers.get() || mc.player == null || !(onHypixelNow() || pretendForTest) || !bedWars) {
            timerLines = List.of();
            timerKey = "";
            return;
        }
        List<String> sidebar = new java.util.ArrayList<>();
        if (objective != null) {
            for (var entry : board.listPlayerScores(objective)) {
                if (!entry.isHidden()) {
                    sidebar.add(net.minecraft.world.scores.PlayerTeam.formatNameForTeam(board.getPlayersTeam(entry.owner()),
                            entry.ownerName()).getString());
                }
            }
        }
        List<io.github.georgwav.cosmetics.sol.client.BedWarsTimers.Label> labels = new java.util.ArrayList<>();
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof net.minecraft.world.entity.decoration.ArmorStand && e.hasCustomName() && e.distanceToSqr(mc.player) < 96 * 96) {
                labels.add(new io.github.georgwav.cosmetics.sol.client.BedWarsTimers.Label(e.getX(), e.getY(), e.getZ(),
                        e.getCustomName().getString()));
            }
        }
        int[] gens = io.github.georgwav.cosmetics.sol.client.BedWarsTimers.nearest(labels, mc.player.getX(), mc.player.getZ());
        String event = io.github.georgwav.cosmetics.sol.client.BedWarsTimers.event(sidebar);
        String key = gens[0] + "|" + gens[1] + "|" + event;
        if (key.equals(timerKey)) {
            return;
        }
        timerKey = key;
        List<String> lines = new java.util.ArrayList<>();
        if (gens[0] >= 0) {
            lines.add(Component.translatable("cosmeticsmod.module.hypixel.timer_diamond", gens[0]).getString());
        }
        if (gens[1] >= 0) {
            lines.add(Component.translatable("cosmeticsmod.module.hypixel.timer_emerald", gens[1]).getString());
        }
        if (event != null) {
            lines.add(event);
        }
        timerLines = List.copyOf(lines);
    }

    /** The Bed Wars timers' lines now (for the self-test). */
    public static List<String> timerLines() {
        return Modules.HYPIXEL.timerLines;
    }

    /** The Bed Wars timers at the top of the screen, in the middle, while there are any. */
    public static void drawTimers(io.github.georgwav.cosmetics.ui.Canvas g, int w, int h) {
        Hypixel hy = Modules.HYPIXEL;
        List<String> lines = hy.timerLines;
        if (!hy.enabled() || lines.isEmpty()) {
            return;
        }
        if (io.github.georgwav.cosmetics.client.hud.HudModule.solLook()) {
            io.github.georgwav.cosmetics.sol.hud.SolHud.textCard(g, w / 2f, 4f, lines);
            return;
        }
        var font = Minecraft.getInstance().font;
        int tw = 0;
        for (String l : lines) {
            tw = Math.max(tw, font.width(l));
        }
        int x = (w - tw) / 2;
        g.fill(x - 4, 3, x + tw + 4, 6 + lines.size() * 10, 0x70000000);
        for (int i = 0; i < lines.size(); i++) {
            g.text(font, lines.get(i), x, 5 + i * 10, 0xFFFFFFFF, true);
        }
    }

    /** For the self-test: whether a line counts as a game's end. */
    public static boolean endsGame(String line) {
        String l = line.toLowerCase(Locale.ROOT);
        return ENDS.stream().anyMatch(l::contains);
    }
}
