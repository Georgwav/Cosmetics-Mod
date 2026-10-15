package io.github.georgwav.cosmetics.sol.ui.screens;

import io.github.georgwav.cosmetics.client.modules.Hypixel;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Quickplay (Hypixel): every game and its modes as chips; a click joins that mode's queue with Hypixel's own
 * {@code /play}. The game you played last is marked, and "Play again" queues it once more.
 */
public final class QuickplayScreen extends SolScreen {
    /** A game and its modes' {@code /play} names, in Hypixel's menu order. */
    public record Game(String key, List<String> modes) {
    }

    public static final List<Game> GAMES = List.of(
            new Game("bedwars", List.of("bedwars_eight_one", "bedwars_eight_two", "bedwars_four_three", "bedwars_four_four", "bedwars_two_four")),
            new Game("skywars", List.of("solo_normal", "solo_insane", "teams_normal", "teams_insane")),
            new Game("duels", List.of("duels_classic_duel", "duels_sumo_duel", "duels_uhc_duel", "duels_bridge_duel", "duels_op_duel",
                    "duels_bow_duel", "duels_combo_duel", "duels_boxing_duel")),
            new Game("murder", List.of("murder_classic", "murder_double_up", "murder_assassins", "murder_infection")),
            new Game("build_battle", List.of("build_battle_solo_normal", "build_battle_teams_normal", "build_battle_solo_pro",
                    "build_battle_guess_the_build")),
            new Game("tnt", List.of("tnt_tntrun", "tnt_pvprun", "tnt_bowspleef", "tnt_tntag", "tnt_capture")),
            new Game("arcade", List.of("arcade_party_games_1", "arcade_hole_in_the_wall", "arcade_zombies_dead_end", "arcade_dropper")),
            new Game("more", List.of("pit", "skyblock", "uhc_solo", "mw_standard", "blitz_solo_normal")));

    private final Ui ui = new Ui();

    public QuickplayScreen(Screen parent) {
        super(Component.translatable("sol.quickplay.title"), parent, Feature.MENU);
    }

    @Override
    protected boolean press(float x, float y, int button) {
        return ui.press(x, y);
    }

    @Override
    protected boolean release(float x, float y, int button) {
        return ui.release();
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    @Override
    protected void frame(Gfx g) {
        Tokens tk = ThemeState.tokens();
        ui.begin(g, mx, my);
        g.rect(g.d.left(), g.d.top(), g.d.right() - g.d.left(), g.d.bottom() - g.d.top(), 0x99050508);
        float w = 900f, h = 120f + GAMES.size() * 50f, x = 640f - w / 2f, y = 360f - h / 2f;
        g.glow(x, y, w, h, 18f, 26f, Colors.withAlpha(tk.glow, 0.16f));
        g.round(x, y, w, h, 18f, Colors.withAlpha(tk.panel, Math.max(0.85f, tk.glass)));
        g.stroke(x, y, w, h, 18f, Math.max(1f, g.d.u), Colors.withAlpha(tk.rim, 0.5f));
        g.highlight(x, y, w, h, 18f, 0x30FFFFFF);
        Icons.draw(g, "spark", x + 28f, y + 26f, 24f, tk.accent, Icons.FILL_ON, 0);
        SolFont h1 = g.font(SolFont.Family.SEMIBOLD, 20f), label = g.font(SolFont.Family.MEDIUM, 13f);
        g.textMid(h1, tr("sol.quickplay.title"), x + 64f, y + 38f, 0xFFFFFFFF);
        String last = Hypixel.lastGame();
        float right = x + w - 24f;
        if (last != null) {
            ui.button(tk, right, y + 38f, tr("sol.quickplay.again"), true, () -> play(last));
        }
        float rowY = y + 92f;
        for (Game game : GAMES) {
            g.textMid(label, tr("sol.quickplay.game." + game.key()), x + 28f, rowY, 0xCCFFFFFF);
            float cx = x + 190f;
            for (String mode : game.modes()) {
                String name = tr("sol.quickplay.mode." + mode);
                cx += ui.chip(tk, cx, rowY, name, mode.equals(last), () -> play(mode)) + 8f;
            }
            rowY += 50f;
        }
        if (!Hypixel.onHypixelNow()) {
            g.textMid(g.font(SolFont.Family.MEDIUM, 12f), tr("sol.quickplay.not_hypixel"), x + 28f, y + h - 22f, 0xFFFFC35C);
        }
    }

    private void play(String mode) {
        Hypixel.play(minecraft, mode);
        onClose();
    }
}
