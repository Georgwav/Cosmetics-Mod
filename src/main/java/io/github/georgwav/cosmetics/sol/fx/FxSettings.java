package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The standard settings every effect has (C.2), made for one effect of a module and shown in its own group: its style
 * (or random from the ticked styles), colour (the preset's, your own, a rainbow or two colours), size, amount, speed
 * and duration (50 to 200%), glow (soft, off, strong), pixel snap, sound (one of Sol's packs, yours, or off) with its
 * volume, who it's for, and whether it shows in first person. Only the ones that apply to the effect are made.
 */
public final class FxSettings {
    public final String group;
    public final List<String> styles;
    public Setting.Toggle on;
    public Setting.Choice style;
    public Setting.Toggle random;
    public final List<Setting.Toggle> ticked = new ArrayList<>();
    public Setting.Choice colourMode;
    public Setting.Color colour, colour2;
    public Setting.Slider size, amount, speed, duration;
    public Setting.Choice glow;
    public Setting.Toggle snap;
    public Setting.Choice sound;
    public Setting.Slider volume;
    public Setting.Choice who;
    public Setting.Toggle firstPerson;

    FxSettings(String group, List<String> styles) {
        this.group = group;
        this.styles = styles;
    }

    /** What to make: each piece is optional. */
    public static final class Spec {
        boolean toggle, random, sound, who, firstPerson, speed = true, amount = true, duration = true, glow = true;
        boolean onByDefault;
        Boolean onWas;
        String defaultSound = "tick";
        boolean playersOnly = true;
        int defaultColour = 0xFF5FD4FF;
        int secondColour = 0xFFFF6EC7;
        String colourMode = "theme";

        /** A switch for the effect itself (when its module has several). */
        public Spec toggle(boolean on) {
            toggle = true;
            onByDefault = on;
            return this;
        }

        /** The switch's default before this one (see {@link Setting#was}). */
        public Spec toggleWas(boolean old) {
            onWas = old;
            return this;
        }

        public Spec random() {
            random = true;
            return this;
        }

        public Spec sound(String def) {
            sound = true;
            defaultSound = def;
            return this;
        }

        public Spec who(boolean playersOnlyDefault) {
            who = true;
            playersOnly = playersOnlyDefault;
            return this;
        }

        public Spec firstPerson() {
            firstPerson = true;
            return this;
        }

        public Spec noSpeed() {
            speed = false;
            return this;
        }

        public Spec noAmount() {
            amount = false;
            return this;
        }

        public Spec noDuration() {
            duration = false;
            return this;
        }

        public Spec noGlow() {
            glow = false;
            return this;
        }

        public Spec colours(int custom, int second) {
            defaultColour = custom;
            secondColour = second;
            return this;
        }

        /** Its own colour by default (Custom) instead of the preset's: a gold totem stays gold. */
        public Spec ownColour(int custom) {
            defaultColour = custom;
            colourMode = "custom";
            return this;
        }
    }

    /** Makes the settings through {@code add} (the module's own), each in group {@code group}. */
    static FxSettings make(String group, List<String> styles, Spec spec, Consumer<Setting<?>> add, java.util.Map<Setting<?>, String> std) {
        FxSettings f = new FxSettings(group, styles);
        String p = group + "_";
        if (spec.toggle) {
            Setting.Toggle on = new Setting.Toggle(p + "on", spec.onByDefault).group(group);
            f.on = put(add, std, "on", spec.onWas != null ? on.was(spec.onWas) : on);
        }
        if (styles.size() > 1) {
            f.style = put(add, std, "style", new Setting.Choice(p + "style", styles.get(0), styles).group(group));
            if (spec.random) {
                f.random = put(add, std, "random", new Setting.Toggle(p + "random", false).group(group));
                Setting.Toggle r = f.random;
                for (String s : styles) {
                    Setting.Toggle t = new Setting.Toggle(p + "tick_" + s, true).group(group);
                    t.shownWhen(r::get);
                    f.ticked.add(put(add, std, "tick:" + s, t));
                }
                f.style.shownWhen(() -> !r.get());
            }
        }
        f.colourMode = put(add, std, "colour_mode", new Setting.Choice(p + "colour_mode", spec.colourMode, List.of("theme", "custom", "rainbow", "two"))
                .group(group));
        Setting.Choice mode = f.colourMode;
        f.colour = put(add, std, "colour", new Setting.Color(p + "colour", spec.defaultColour).group(group));
        f.colour.shownWhen(() -> mode.get().equals("custom") || mode.get().equals("two"));
        f.colour2 = put(add, std, "colour_two", new Setting.Color(p + "colour_two", spec.secondColour).group(group));
        f.colour2.shownWhen(() -> mode.get().equals("two"));
        f.size = put(add, std, "size", percent(p + "size", group));
        if (spec.amount) {
            f.amount = put(add, std, "amount", percent(p + "amount", group));
        }
        if (spec.speed) {
            f.speed = put(add, std, "speed", percent(p + "speed", group));
        }
        if (spec.duration) {
            f.duration = put(add, std, "duration", percent(p + "duration", group));
        }
        if (spec.glow) {
            f.glow = put(add, std, "glow", new Setting.Choice(p + "glow", "soft", List.of("soft", "off", "strong")).group(group));
        }
        f.snap = put(add, std, "snap", new Setting.Toggle(p + "snap", false).was(true).group(group));
        if (spec.sound) {
            List<String> choices = SolSounds.choices();
            f.sound = put(add, std, "sound", new Setting.Choice(p + "sound", spec.defaultSound, choices).group(group));
            Setting.Choice snd = f.sound;
            f.volume = put(add, std, "volume", new Setting.Slider(p + "volume", 100, 0, 200, 10, v -> Math.round(v) + "%").group(group));
            f.volume.shownWhen(() -> !snd.get().equals("off"));
        }
        if (spec.who) {
            f.who = put(add, std, "who", new Setting.Choice(p + "who", spec.playersOnly ? "players" : "everything",
                    List.of("players", "everything")).group(group));
        }
        if (spec.firstPerson) {
            f.firstPerson = put(add, std, "first_person", new Setting.Toggle(p + "first_person", false).group(group));
        }
        if (f.on != null) {
            // The rest of the effect's settings only while it's on.
            Setting.Toggle on = f.on;
            for (java.util.Map.Entry<Setting<?>, String> e : std.entrySet()) {
                if (e.getKey() != on && e.getKey().group.equals(group)) {
                    e.getKey().alsoShownWhen(on::get);
                }
            }
        }
        return f;
    }

    private static <S extends Setting<?>> S put(Consumer<Setting<?>> add, java.util.Map<Setting<?>, String> std, String key, S s) {
        add.accept(s);
        std.put(s, key);
        return s;
    }

    private static Setting.Slider percent(String id, String group) {
        return new Setting.Slider(id, 100, 50, 200, 10, v -> Math.round(v) + "%").group(group);
    }

    // ------------------------------------------------------------------ using them

    /** Whether the effect is on (always, when it has no switch of its own). */
    public boolean on() {
        return on == null || on.get();
    }

    /** The style random picked last (-1: none yet). */
    private int lastPicked = -1;

    /** The style to play: the picked one, or one of the ticked ones at random (never the last one twice running). */
    public int pick(int seed) {
        if (style == null) {
            return 0;
        }
        if (random != null && random.get()) {
            int n = 0;
            for (Setting.Toggle t : ticked) {
                if (t.get()) {
                    n++;
                }
            }
            if (n > 0) {
                // Never the same one twice running, when there's another to pick.
                boolean skipLast = n > 1 && lastPicked >= 0 && lastPicked < ticked.size() && ticked.get(lastPicked).get();
                int k = (int) (FxDraw.hash(seed, 99) * (skipLast ? n - 1 : n));
                for (int i = 0; i < ticked.size(); i++) {
                    if (ticked.get(i).get() && !(skipLast && i == lastPicked) && k-- == 0) {
                        lastPicked = i;
                        return i;
                    }
                }
            }
        }
        return Math.max(0, styles.indexOf(style.get()));
    }

    public String styleName(int i) {
        return styles.get(Math.max(0, Math.min(styles.size() - 1, i)));
    }

    /** The effect's length for a style that lasts {@code base} seconds at 100%. */
    public float life(float base) {
        float k = duration == null ? 1f : (float) (duration.get() / 100.0);
        return base * k;
    }

    /** Fills a starting effect's colours and standard numbers. */
    public void apply(Fx fx, int styleIndex) {
        fx.style = styleIndex;
        FxColour.apply(fx, colourMode.get(), colour.argb(), colour2.argb());
        fx.size = (float) (size.get() / 100.0);
        fx.amount = amount == null ? 1f : (float) (amount.get() / 100.0);
        fx.speed = speed == null ? 1f : (float) (speed.get() / 100.0);
        fx.glow = glow == null ? 1 : switch (glow.get()) {
            case "off" -> 0;
            case "strong" -> 2;
            default -> 1;
        };
        fx.snap = snap.get();
    }

    /** Plays one of the effect's sounds (when it has a sound setting and it isn't off). */
    public void play(String event, float pitch) {
        if (sound != null) {
            SolSounds.play(sound.get(), event, (float) (volume.get() / 100.0), pitch);
        }
    }

    /** Whether the effect is for {@code e} (players only, or everything you hit). */
    public boolean allows(Entity e) {
        return who == null || who.get().equals("everything") || e instanceof Player;
    }

    /** Whether it shows in first person (for effects on yourself). */
    public boolean inFirstPerson() {
        return firstPerson != null && firstPerson.get();
    }
}
