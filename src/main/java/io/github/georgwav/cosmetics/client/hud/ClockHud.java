package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.client.Minecraft;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** The time on your computer's clock. */
public final class ClockHud extends TextHud {
    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);
    private final Setting.Choice format = add(new Setting.Choice("format", "24h", List.of("24h", "12h")));

    public ClockHud() {
        super("clock", "clock", 1f, 0f, -4f, 4f);
    }

    @Override
    protected List<String> lines(Minecraft mc) {
        return List.of(LocalTime.now().format(format.get().equals("12h") ? H12 : H24));
    }
}
