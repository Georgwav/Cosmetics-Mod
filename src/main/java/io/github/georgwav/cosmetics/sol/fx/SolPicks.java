package io.github.georgwav.cosmetics.sol.fx;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.MainFolder;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.util.AtomicFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sol picks (C.2): one chip on the Modules page that switches on a curated set of effects (a Soul rise kill, Pixels
 * hits with the hitmarker and the Tick sound, the totem Shatter and Pop ghost, the Ring target mark, the Square ring
 * on landing, rain ripples, chunk rise and cosmos break, and Sol toasts, the kill feed and combo) and off again: what
 * each touched setting and module was before is kept (client/sol-picks.json) and put back.
 */
public final class SolPicks {
    /** One pick: a module to switch on, or a setting's value. */
    private record Pick(Module module, Setting<Object> setting, Object value) {
    }

    private static final List<Pick> PICKS = new ArrayList<>();
    private static Boolean on;

    private SolPicks() {
    }

    /** A module Sol picks switches on. */
    public static void module(Module m) {
        PICKS.add(new Pick(m, null, null));
    }

    /** A setting Sol picks sets to {@code value}. */
    @SuppressWarnings("unchecked")
    public static <T> void set(Setting<T> s, T value) {
        PICKS.add(new Pick(null, (Setting<Object>) (Setting<?>) s, value));
    }

    private static Path file() {
        return MainFolder.clientFolder().resolve("sol-picks.json");
    }

    /** Whether Sol picks is on (it was switched on and not off since). */
    public static boolean on() {
        if (on == null) {
            on = Files.isRegularFile(file());
        }
        return on;
    }

    /** Switches the set on (keeping what was there) or off (putting it back). */
    public static void toggle() {
        if (on()) {
            off();
        } else {
            apply();
        }
    }

    private static String key(Pick p) {
        return p.module != null ? "module:" + p.module.id : owner(p.setting) + ":" + p.setting.id;
    }

    private static String owner(Setting<?> s) {
        for (Module m : Modules.all()) {
            if (m.settings().contains(s)) {
                return m.id;
            }
        }
        return "?";
    }

    private static void apply() {
        JsonObject before = new JsonObject();
        for (Pick p : PICKS) {
            if (p.module != null) {
                before.addProperty(key(p), p.module.enabled());
            } else {
                before.addProperty(key(p), String.valueOf(p.setting.get()));
            }
        }
        try {
            Files.createDirectories(file().getParent());
            AtomicFiles.writeString(file(), before.toString());
        } catch (Exception e) {
            CosmeticsMod.LOGGER.warn("[Sol] Sol picks: couldn't keep the settings before: {}", e.toString());
        }
        for (Pick p : PICKS) {
            if (p.module != null) {
                p.module.setEnabled(true);
            } else {
                p.setting.set(p.value);
            }
        }
        on = true;
        Modules.save();
    }

    private static void off() {
        JsonObject before = null;
        try {
            before = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
        } catch (Exception e) {
            CosmeticsMod.LOGGER.debug("[Sol] Sol picks: nothing kept: {}", e.toString());
        }
        for (Pick p : PICKS) {
            String k = key(p);
            if (p.module != null) {
                boolean was = before != null && before.has(k) && before.get(k).getAsBoolean();
                p.module.setEnabled(was);
            } else if (before != null && before.has(k)) {
                Object old = parse(p.setting, before.get(k).getAsString());
                if (old != null) {
                    p.setting.set(old);
                }
            } else {
                p.setting.reset();
            }
        }
        try {
            Files.deleteIfExists(file());
        } catch (Exception e) {
            CosmeticsMod.LOGGER.debug("[Sol] Sol picks: {}", e.toString());
        }
        on = false;
        Modules.save();
    }

    private static Object parse(Setting<?> s, String text) {
        try {
            if (s.def instanceof Boolean) {
                return Boolean.parseBoolean(text);
            }
            if (s.def instanceof Double) {
                return Double.parseDouble(text);
            }
            if (s.def instanceof Integer) {
                return Integer.parseInt(text);
            }
            return text;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
