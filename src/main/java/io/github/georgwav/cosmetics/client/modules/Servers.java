package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * Servers: auto reconnect. When a server drops you, the disconnect screen counts down and joins it again (its
 * button reconnects at once, or stops the countdown). And the server list upgrade (ServerListUpgrade): pings in
 * milliseconds, players online, folders.
 */
public final class Servers extends Module {
    private final Setting.Slider delay = add(new Setting.Slider("reconnect_delay", 5, 1, 30, 1, v -> Math.round(v) + " s"));
    private final Setting.Toggle serverList = add(new Setting.Toggle("server_list", true));
    private ServerData last;
    private int countdown = -1;
    private Object countingFor;
    /** The disconnect screen's reconnect button (its label counts down). */
    public static net.minecraft.client.gui.components.Button button;

    public Servers() {
        super("servers", Category.TOOLS, "server");
    }

    @Override
    protected void tick(Minecraft mc) {
        if (mc.getCurrentServer() != null) {
            last = mc.getCurrentServer();
        }
        var screen = io.github.georgwav.cosmetics.ui.ScreenOpener.current();
        if (!(screen instanceof DisconnectedScreen) || last == null) {
            countdown = -1;
            countingFor = null;
            button = null;
            return;
        }
        if (countingFor != screen) {
            countingFor = screen;
            countdown = (int) Math.round(delay.get()) * 20;
        }
        if (countdown > 0 && --countdown == 0) {
            reconnect(mc);
        }
        if (button != null) {
            button.setMessage(label());
        }
    }

    /** The reconnect button's label: the countdown, or "Reconnect". */
    public static net.minecraft.network.chat.Component label() {
        int s = secondsLeft();
        return s > 0 ? net.minecraft.network.chat.Component.translatable("sol.servers.reconnecting", s)
                : net.minecraft.network.chat.Component.translatable("sol.servers.reconnect");
    }

    /** Seconds left before reconnecting, or -1 when not counting. */
    public static int secondsLeft() {
        Servers s = Modules.SERVERS;
        return s.enabled() && s.countdown > 0 ? (s.countdown + 19) / 20 : -1;
    }

    /** Whether the server list upgrade is on. */
    public static boolean serverList() {
        Servers s = Modules.SERVERS;
        return s.enabled() && s.serverList.get();
    }

    /** Whether there is a server to reconnect to. */
    public static boolean canReconnect() {
        Servers s = Modules.SERVERS;
        return s.enabled() && s.last != null;
    }

    public static void stop() {
        Modules.SERVERS.countdown = 0;
    }

    public static void reconnect(Minecraft mc) {
        Servers s = Modules.SERVERS;
        if (s.last == null) {
            return;
        }
        s.countdown = -1;
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(s.last.ip),
                s.last, false, null);
    }
}
