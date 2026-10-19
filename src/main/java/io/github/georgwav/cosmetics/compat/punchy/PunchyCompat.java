package io.github.georgwav.cosmetics.compat.punchy;

import io.github.georgwav.cosmetics.CosmeticsMod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Punchy's own switches, through its public config API ({@code PunchyConfig.snapshot()} and {@code applyAndSave}),
 * by reflection: Punchy is optional and never bundled. Only touched behind {@code Compat.PUNCHY}.
 */
public final class PunchyCompat {
    /**
     * What the Punchy module's PvP mode sets: slower, calmer animations and none of the extra movement (the custom
     * walk, the sprint and fall swings, arm physics, shakes and particles).
     */
    private static final Map<String, Object> CALM = new LinkedHashMap<>();

    static {
        CALM.put("animationSpeed", 3.5f);
        CALM.put("enableCustomWalk", false);
        CALM.put("enableSprintArmSwing", false);
        CALM.put("enableFallArmAnimation", false);
        CALM.put("enableFreezeShake", false);
        CALM.put("enableParticles", false);
        CALM.put("enableSweat", false);
        CALM.put("disableArmPhysics", true);
    }

    private static Method snapshot, applyAndSave;
    private static boolean broken;

    private PunchyCompat() {
    }

    private static boolean ready() {
        if (snapshot != null) {
            return true;
        }
        if (broken) {
            return false;
        }
        try {
            Class<?> config = Class.forName("punchy.config.PunchyConfig");
            Class<?> data = Class.forName("punchy.config.PunchyConfig$Data");
            snapshot = config.getMethod("snapshot");
            applyAndSave = config.getMethod("applyAndSave", data);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            broken = true;
            CosmeticsMod.LOGGER.warn("[Sol] Punchy's config isn't where Sol expects it; the Punchy module can't switch it: {}", e.toString());
            return false;
        }
    }

    /**
     * Switches Punchy on or off and its calm PvP values on or off. While calm is on, {@code own} keeps the player's
     * own values of what it changed, put back when it goes off. Writes Punchy's config only when something changed.
     */
    public static void apply(boolean on, boolean calm, Map<String, Object> own) {
        if (!ready()) {
            return;
        }
        try {
            Object d = snapshot.invoke(null);
            boolean changed = set(d, "enableMod", on);
            if (calm) {
                for (Map.Entry<String, Object> e : CALM.entrySet()) {
                    if (!own.containsKey(e.getKey())) {
                        own.put(e.getKey(), get(d, e.getKey()));
                    }
                    changed |= set(d, e.getKey(), e.getValue());
                }
            } else if (!own.isEmpty()) {
                for (Map.Entry<String, Object> e : own.entrySet()) {
                    changed |= set(d, e.getKey(), e.getValue());
                }
                own.clear();
            }
            if (changed) {
                applyAndSave.invoke(null, d);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            snapshot = null;
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't set Punchy's config: {}", e.toString());
        }
    }

    /**
     * Sol's preset for Punchy's hands (Georg's tuning: smaller and nearer the middle, swords and blocks set right),
     * applied through Punchy's public tuning API ({@code PunchyTuningConfig.applyProfileFromFileLight} then
     * {@code saveCurrent}), so it becomes Punchy's own and stays until the player changes it there. Whether it was
     * applied (false when Punchy has no such API: then it isn't tried again).
     */
    public static boolean applyTuning(java.nio.file.Path folder) {
        try (java.io.InputStream in = PunchyCompat.class.getResourceAsStream("/assets/cosmeticsmod/punchy/sol_tuning.json")) {
            if (in == null) {
                return false;
            }
            Class<?> tuning = Class.forName("punchy.config.PunchyTuningConfig");
            Method apply = tuning.getMethod("applyProfileFromFileLight", java.nio.file.Path.class);
            Method save = tuning.getMethod("saveCurrent");
            java.nio.file.Files.createDirectories(folder);
            java.nio.file.Path file = folder.resolve("punchy_sol_tuning.json");
            java.nio.file.Files.write(file, in.readAllBytes());
            boolean ok = Boolean.TRUE.equals(apply.invoke(null, file));
            if (ok) {
                save.invoke(null);
            }
            CosmeticsMod.LOGGER.info("[Sol] Punchy's hands set to Sol's preset{}", ok ? "" : " (Punchy didn't take it)");
            return ok;
        } catch (ReflectiveOperationException | LinkageError | java.io.IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't give Punchy Sol's hand preset: {}", e.toString());
            return false;
        }
    }

    /** The settings PvP mode changes, for reading saved values back with the right type. */
    public static boolean calms(String field) {
        return CALM.containsKey(field);
    }

    /** A saved value as the field's type (a float field comes back from JSON as a double). */
    public static Object typed(String field, Object value) {
        Object calm = CALM.get(field);
        if (calm instanceof Float && value instanceof Number n) {
            return n.floatValue();
        }
        if (calm instanceof Boolean && value instanceof Boolean) {
            return value;
        }
        return null;
    }

    private static Object get(Object data, String name) throws ReflectiveOperationException {
        return data.getClass().getField(name).get(data);
    }

    private static boolean set(Object data, String name, Object value) throws ReflectiveOperationException {
        Field f = data.getClass().getField(name);
        if (value.equals(f.get(data))) {
            return false;
        }
        f.set(data, value);
        return true;
    }
}
