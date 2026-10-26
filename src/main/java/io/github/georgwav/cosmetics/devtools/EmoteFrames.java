package io.github.georgwav.cosmetics.devtools;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.emote.Emote;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.emote.Emotes;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * A look at an emote as the game plays it ({@code -Dcosmeticsmod.emoteFrames=<emote id>}, local only): once in the
 * world you face the camera in third person (a little from above, the game's own sky, the HUD hidden), the emote plays,
 * and every second tick (10 a second) the game's frame is saved in the game folder as
 * {@code emote-frame-<milliseconds into the emote>.png}. Then the game closes.
 */
public final class EmoteFrames {
    private static final String ID = System.getProperty("cosmeticsmod.emoteFrames", "");
    /** Ticks in the world before the emote starts: chunks drawn and the scenario's own staging over. */
    private static final int SETTLE = 200;
    private int ticks;
    private int start = -1;
    private long startNanos;
    private float length;
    private boolean stopping;

    private EmoteFrames() {
    }

    public static void init() {
        if (ID.isEmpty()) {
            return;
        }
        EmoteFrames f = new EmoteFrames();
        ClientTickEvents.END_CLIENT_TICK.register(f::tick);
        CosmeticsMod.LOGGER.info("[Sol] Emote frames armed for {}", ID);
    }

    private void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        ticks++;
        if (ticks < SETTLE) {
            return;
        }
        if (start < 0) {
            Emote emote = Emotes.get(ID);
            if (emote == null) {
                CosmeticsMod.LOGGER.warn("[Sol] Emote frames: no emote {}", ID);
                mc.stop();
                return;
            }
            // Looking up a little puts the front camera above you, looking down: head to feet in view.
            mc.player.setXRot(-15f);
            // The body faces where the head does (it lags behind the head while you stand), square to the camera.
            mc.player.setYHeadRot(mc.player.getYRot());
            mc.player.setYBodyRot(mc.player.getYRot());
            mc.player.yBodyRotO = mc.player.getYRot();
            mc.options.fov().set(50);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            io.github.georgwav.cosmetics.sol.render.Mc.hideHud(true);
            io.github.georgwav.cosmetics.client.Modules.ATMOSPHERE.setEnabled(false);
            EmotePlayer.play(emote);
            startNanos = System.nanoTime();
            start = ticks;
            length = emote.length();
            CosmeticsMod.LOGGER.info("[Sol] Emote frames: playing {} ({} s); sound {}: {}", ID, length, emote.sound(),
                    emote.sound() == null ? "none" : EmotePlayer.soundFound(emote) ? "found" : "not in any pack");
            return;
        }
        int t = ticks - start;
        // The emote runs on the clock, not on ticks: each frame is named by where in the emote it is.
        long ms = (System.nanoTime() - startNanos) / 1_000_000L;
        if (t % 2 == 0 && ms <= length * 1000f) {
            FrameGrab.grab(mc, String.format(java.util.Locale.ROOT, "emote-frame-%05d", ms));
        }
        if (!stopping && ms > length * 1000f + 500f && FrameGrab.PENDING.get() == 0) {
            stopping = true;
            CosmeticsMod.LOGGER.info("[Sol] Emote frames: {} frames written", t / 2);
            mc.stop();
        }
    }
}
