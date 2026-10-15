package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;

/**
 * Social: streamer mode, for recording and streaming: your coordinates and the server's address are hidden from the
 * HUD modules while it's on.
 */
public final class Social extends Module {
    private final Setting.Toggle streamer = add(new Setting.Toggle("streamer_mode", true));

    public Social() {
        super("social", Category.CHAT, "people");
    }

    /** Whether streamer mode hides your coordinates and the server now. */
    public static boolean streaming() {
        Social s = Modules.SOCIAL;
        return s.enabled() && s.streamer.get();
    }
}
